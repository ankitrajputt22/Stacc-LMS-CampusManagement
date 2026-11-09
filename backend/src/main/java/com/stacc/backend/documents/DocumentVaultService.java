package com.stacc.backend.documents;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.Mac;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.token.JwtProperties;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The document vault and certificate requests. Owners upload their own files and request certificates; admins with
 * DOCUMENT_ISSUE issue a certificate file into the requester's vault or refuse with a note, and may withdraw an issued
 * document. An issued document's verification code is derived from its ID with the server's signing key (so its owner
 * can always see it and nothing secret is stored); the public check looks it up by the code's fingerprint and says only
 * whether it is an authentic, current document, with its title and issue date.
 */
@Service
@Transactional
public class DocumentVaultService {

    public record DocumentView(Long documentId, VaultDocument.Origin origin, String title, String fileName, long sizeBytes,
            Instant createdAt, String issuedByLoginId, String verificationCode, boolean withdrawn) {
    }

    public record RequestView(Long requestId, String requesterLoginId, CertificateRequest.Type type, String purpose,
            CertificateRequest.Status status, String decisionNote, Instant createdAt, Instant decidedAt) {
    }

    /** The public answer: only whether the document is genuine and current, with its title and issue date. */
    public record Authenticity(boolean authentic, String message, String title, Instant issuedAt) {
    }

    private final VaultDocumentRepository documents;
    private final CertificateRequestRepository requests;
    private final UserAccountRepository accounts;
    private final LmsAttachmentStore files;
    private final JwtProperties jwt;
    private final Clock clock;

    public DocumentVaultService(VaultDocumentRepository documents, CertificateRequestRepository requests, UserAccountRepository accounts,
            LmsAttachmentStore files, JwtProperties jwt, Clock clock) {
        this.documents = documents;
        this.requests = requests;
        this.accounts = accounts;
        this.files = files;
        this.jwt = jwt;
        this.clock = clock;
    }

    // --- Owners

    @Transactional(readOnly = true)
    public List<DocumentView> mine(Long accountId) {
        return documents.findAllForOwner(accountId).stream().map(this::view).toList();
    }

    public DocumentView upload(Long accountId, String title, MultipartFile file) {
        List<StoredFile> stored = files.storeAll("vault/accounts/" + accountId, file == null ? List.of() : List.of(file));
        if (stored.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a file.");
        }
        try {
            return view(documents.saveAndFlush(new VaultDocument(accounts.getReferenceById(accountId), VaultDocument.Origin.UPLOADED,
                    title, stored.get(0), null, null, now())));
        } catch (RuntimeException failure) {
            files.removeQuietly(stored);
            throw failure instanceof IllegalArgumentException ? new ApiException(HttpStatus.BAD_REQUEST, "Give the document a title.") : failure;
        }
    }

    /** Opens one of the owner's own documents; any other answers the same 404. */
    @Transactional(readOnly = true)
    public LmsAttachmentStore.Download open(Long accountId, Long documentId) {
        VaultDocument document = documents.findById(documentId == null ? -1L : documentId)
                .filter(found -> found.getOwner().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found."));
        return files.open(document.getObjectKey(), document.getFileName(), document.getContentType(), document.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public List<RequestView> myRequests(Long accountId) {
        return requests.findAllForRequester(accountId).stream().map(this::view).toList();
    }

    public RequestView request(Long accountId, CertificateRequest.Type type, String purpose) {
        try {
            return view(requests.saveAndFlush(new CertificateRequest(accounts.getReferenceById(accountId), type, purpose, now())));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a certificate and say what it is for.");
        }
    }

    public RequestView cancel(Long accountId, Long requestId) {
        CertificateRequest request = requests.findByIdForUpdate(requestId == null ? -1L : requestId)
                .filter(found -> found.getRequester().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));
        finish(request, CertificateRequest.Status.CANCELLED, accountId, null);
        return view(request);
    }

    // --- The college

    @Transactional(readOnly = true)
    public List<RequestView> queue(CertificateRequest.Status status) {
        return requests.findAllByStatus(status).stream().map(this::view).toList();
    }

    /** Issues the certificate file into the requester's vault and returns the request, now ISSUED. */
    public RequestView issue(Long adminId, Long requestId, String title, MultipartFile file) {
        CertificateRequest request = requests.findByIdForUpdate(requestId == null ? -1L : requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));
        if (request.getStatus() != CertificateRequest.Status.REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This request was already finished.");
        }
        Long ownerId = request.getRequester().getId();
        List<StoredFile> stored = files.storeAll("vault/accounts/" + ownerId, file == null ? List.of() : List.of(file));
        if (stored.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Attach the certificate file.");
        }
        try {
            VaultDocument document = documents.saveAndFlush(new VaultDocument(request.getRequester(), VaultDocument.Origin.ISSUED,
                    title, stored.get(0), accounts.getReferenceById(adminId), request, now()));
            document.verifiableBy(fingerprint(codeFor(document.getId())));
            finish(request, CertificateRequest.Status.ISSUED, adminId, null);
            documents.flush();
        } catch (RuntimeException failure) {
            files.removeQuietly(stored);
            throw failure instanceof IllegalArgumentException ? new ApiException(HttpStatus.BAD_REQUEST, "Give the certificate a title.") : failure;
        }
        return view(request);
    }

    public RequestView refuse(Long adminId, Long requestId, String note) {
        CertificateRequest request = requests.findByIdForUpdate(requestId == null ? -1L : requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));
        finish(request, CertificateRequest.Status.REFUSED, adminId, note);
        return view(request);
    }

    /** Withdraws an issued document: the owner keeps it, but its verification code no longer confirms it. */
    public void withdraw(Long documentId) {
        VaultDocument document = documents.findById(documentId == null ? -1L : documentId)
                .filter(found -> found.getOrigin() == VaultDocument.Origin.ISSUED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Issued document not found."));
        document.withdraw(now());
    }

    // --- Public

    @Transactional(readOnly = true)
    public Authenticity verify(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^D[0-9]{1,18}-[0-9A-F]{16}$")) {
            return new Authenticity(false, "This is not a Stacc verification code.", null, null);
        }
        Optional<VaultDocument> found = documents.findByVerificationFingerprint(fingerprint(normalized));
        if (found.isEmpty()) {
            return new Authenticity(false, "No document has this code.", null, null);
        }
        if (found.get().getWithdrawnAt() != null) {
            return new Authenticity(false, "This document was withdrawn by the college.", null, null);
        }
        return new Authenticity(true, "Authentic: issued by the college through Stacc.", found.get().getTitle(), found.get().getCreatedAt());
    }

    private void finish(CertificateRequest request, CertificateRequest.Status outcome, Long by, String note) {
        try {
            request.decide(outcome, accounts.getReferenceById(by), note, now());
        } catch (IllegalStateException finished) {
            throw new ApiException(HttpStatus.CONFLICT, "This request was already finished.");
        }
    }

    String codeFor(Long documentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(jwt.signingKey());
            byte[] digest = mac.doFinal(("stacc-vault-document:" + documentId).getBytes(StandardCharsets.UTF_8));
            return "D" + documentId + "-" + HexFormat.of().withUpperCase().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String fingerprint(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private DocumentView view(VaultDocument d) {
        return new DocumentView(d.getId(), d.getOrigin(), d.getTitle(), d.getFileName(), d.getSizeBytes(), d.getCreatedAt(),
                d.getIssuedBy() == null ? null : d.getIssuedBy().getLoginId(),
                d.getOrigin() == VaultDocument.Origin.ISSUED ? codeFor(d.getId()) : null, d.getWithdrawnAt() != null);
    }

    private RequestView view(CertificateRequest r) {
        return new RequestView(r.getId(), r.getRequester().getLoginId(), r.getType(), r.getPurpose(), r.getStatus(), r.getDecisionNote(),
                r.getCreatedAt(), r.getDecidedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
