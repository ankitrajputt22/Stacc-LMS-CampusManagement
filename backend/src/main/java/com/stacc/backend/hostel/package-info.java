/**
 * Hostel management: the hostels, rooms, and beds the college has, students' hostel applications for an academic
 * session, and the allocation of approved students to beds. Approval itself is the shared approval engine's job: an
 * application starts an approval request with the {@code HOSTEL_APPLICATION} workflow and reads its status from there,
 * never keeping a copy. Occupancy is derived from active allocations, never stored. See {@code docs/hostel-management.md}.
 */
package com.stacc.backend.hostel;
