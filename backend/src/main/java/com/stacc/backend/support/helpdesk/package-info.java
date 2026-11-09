/**
 * The one helpdesk ticket engine for all of Stacc: academic, LMS, campus, administrative, and technical issues share it,
 * told apart by configurable categories. A requester raises a ticket and follows it; admins work through the queue,
 * assign, prioritise, and move it through its lifecycle. Messages and history are append-only, and nothing is deleted.
 * See {@code docs/helpdesk.md}.
 */
package com.stacc.backend.support.helpdesk;
