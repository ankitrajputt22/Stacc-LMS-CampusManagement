/**
 * The one secure pass service: a pass is a random code tied to one purpose and source record, one holder, and a time
 * window. Only the code's SHA-256 fingerprint is stored; the code is shown once to its holder. Passes are revoked,
 * never deleted. Feature modules issue and verify passes; this module depends on none of them.
 */
package com.stacc.backend.pass;
