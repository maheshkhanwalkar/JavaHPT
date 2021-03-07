package com.revtekk.javahpt.ipc;

/**
 * Shared Memory Inter-process Communication (IPC)
 *
 * This class implements a bi-directional communication between two
 * processes on the same host, by using shared memory.
 *
 * When two processes are on different hosts, then the only real means of
 * communication are via TCP or UDP sockets. However, when it is known that
 * the two processes are indeed on the same host, then using shared memory
 * is possible and is magnitudes faster than localhost sockets.
 */
public class SharedMem
{

}
