package com.revtekk.javahpt.ipc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Memory-mapped socket
 *
 * This class encapsulates a named, memory-mapped "socket" which offers
 * a bi-directional communication medium.
 *
 * This is accomplished using a memory mapped file, which is split into
 * two buffer sections -- with each section used for uni-directional
 * communication.
 *
 *      Layout of the memory-mapped file:
 *
 * |-------------------|----------------------------------|----------------------------------|
 * | Control Variables | Server -> Client Circular Buffer | Client -> Server Circular Buffer |
 * |-------------------|----------------------------------|----------------------------------|
 *
 * The control variables section at the very front of the memory-mapped file
 * contains the read and write positions for the server and client for each of
 * the circular buffers. This allows each side to know:
 *
 *   1. How much space is left in the buffer
 *   2. Whether there is additional data to read
 *
 *     Layout of the control variables:
 *
 * |------------------|-------------------|------------------|-------------------|--------|
 * | Server Read Head | Server Write Head | Client Read Head | Client Write Head | Status |
 * |------------------|-------------------|------------------|-------------------|--------|
 *
 * The status section is used to handle the edge case of circular buffers:
 * determining whether the buffer is full or empty.
 *
 *     Layout of the status section:
 *
 * |-----------------------|------------------------|-----------------------|------------------------|
 * | Server -> Client FULL | Server -> Client EMPTY | Client -> Server FULL | Client -> Server EMPTY |
 * |-----------------------|------------------------|-----------------------|------------------------|
 *
 * No special care needs to be taken for synchronization of the control variables,
 * because even if they change during a read/write operation by the other side of
 * the connection, it will never cause an operation to fail.
 *
 * For example, (1) can change if the reader reads more (more space is left now),
 * but that will not cause the write to ever fail. For (2), more data can come in later,
 * which will be detected and read on the next call to read(...)
 *
 * This class can be used directly in scenarios where client management is not
 * required on the server-side, i.e. only one or a small, fixed number of clients
 * is expected. If that is not the case, use the SharedMem class.
 *
 *
 * [Limitations]
 *   This implementation does not support multiple writers for a single direction
 *   in the socket, i.e. multiple threads writing in the Server -> Client buffer.
 *
 *   If that use-case is required, calls to write(...) should be protected from
 *   multiple parallel invocations, i.e. through some sort of locking mechanism.
 *
 *   The eventual goal is to remove this limitation, allowing for parallel writes
 *   to take place. Stay tuned!
 */
public class MemSocket
{
    private final RandomAccessFile file;
    private final MappedByteBuffer buffer;

    private final boolean server;
    private final int stoCBufSize, ctoSBufSize;

    // Control variable positions
    private static final int SERVER_READ = 0, SERVER_WRITE = 4,
            CLIENT_READ = 8, CLIENT_WRITE = 12,
            S_C_FULL = 16, S_C_EMPTY = 17, C_S_FULL = 18, C_S_EMPTY = 19;

    private static final int STATUS_SET = 1, STATUS_UNSET = 0;

    // Start of the first buffer -- always at position 20 within the memory mapped file, since
    // it always immediately follows the control variables
    private static final int FIRST_START = 20;

    /**
     * Create a new memory-mapped socket
     * @param name name of the underlying mapped file
     * @param stoCBufSize size of the server->client buffer
     * @param ctoSBufSize size of the client->server buffer
     * @param server whether this socket represents a server or client
     * @throws IOException if the memory-mapped file cannot be created
     */
    public MemSocket(String name, int stoCBufSize, int ctoSBufSize, boolean server) throws IOException
    {
        this.file = new RandomAccessFile(name, "rw");
        this.file.setLength(0);
        long size = FIRST_START + stoCBufSize + ctoSBufSize;

        this.stoCBufSize = stoCBufSize;
        this.ctoSBufSize = ctoSBufSize;

        this.buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
        this.server = server;

        if(server) {
            buffer.putInt(SERVER_READ, stoCBufSize);
            buffer.putInt(SERVER_WRITE, 0);

            // Set buffer statuses
            buffer.put(S_C_FULL, (byte)0);
            buffer.put(S_C_EMPTY, (byte)1);
            buffer.put(C_S_FULL, (byte)0);
            buffer.put(C_S_EMPTY, (byte)1);
        } else {
            buffer.putInt(CLIENT_READ, 0);
            buffer.putInt(CLIENT_WRITE, stoCBufSize);
        }
    }

    public int write(byte[] input)
    {
        return write(input, 0, input.length);
    }

    public int write(byte[] input, int offset, int length)
    {
        // Determine which control variables need to be checked/updated
        final int writeVar = server ? SERVER_WRITE : SERVER_READ;
        final int readVar = server ? CLIENT_READ : SERVER_READ;

        final int fullVar = server ? S_C_FULL : C_S_FULL;
        final int emptyVar = server ? S_C_EMPTY : C_S_EMPTY;

        final int bufOffset = server ? FIRST_START : FIRST_START + stoCBufSize;
        final int end = server ? stoCBufSize : ctoSBufSize;

        // Buffer full -- cannot write anything!
        if(buffer.get(fullVar) == STATUS_SET)
            return 0;

        final int read = buffer.getInt(readVar);
        final int write = buffer.getInt(writeVar);


        /*
         * Handle the situation where the write head is before the
         * read head, which indicates only the space between is available.
         *
         * Everything else is to be considered occupied, so it cannot be
         * used to fulfill the write request
         *
         *              W              R
         *              |              |
         *              V              V
         * ----------------------------------------------------
         * | ---------- |     avail    | -------------------- |
         * ----------------------------------------------------
         *
         * Therefore, check whether 'avail' is enough space for the request,
         * writing up to 'avail' bytes and marking the FULL status if the buffer
         * indeed becomes full after performing the write.
         *
         * Write operations -- assuming they write at least 1 byte -- will always
         * unset the EMPTY status.
         */
        if(read > write) {
            final int avail = read - write;
            final int actual = Math.min(length, avail);

            // Write data and update the write head
            buffer.put(bufOffset + write, input, offset, actual);
            buffer.putInt(writeVar, write + actual);

            // Update status
            buffer.putInt(emptyVar, STATUS_UNSET);

            if(write + actual == read) {
                buffer.putInt(fullVar, STATUS_SET);
            }

            return actual;
        }

        /*
         * Handle the situation where the write head is after
         * the read head, which indicates there are two regions which
         * are available for writing.
         *
         * The situation where write == read is also covered here, since
         * if the control flow reaches here, then it means the buffer is
         * empty -- since the FULL status was already checked earlier.
         *
         *              R              W
         *              |              |
         *              V              V
         * ----------------------------------------------------
         * |  avail #2  | ------------ |     available #1     |
         * ----------------------------------------------------
         *
         * If both the available sections are used up in fulfilling the
         * write request, then the FULL status is set.
         *
         * Write operations -- assuming they write at least 1 byte -- will
         * always unset the EMPTY status.
         */
        int avail = end - write;
        int actual = Math.min(length, avail);

        buffer.put(bufOffset + write, input, offset, actual);

        // Request could be satisfied with just the first available region
        if(actual == length) {
            buffer.putInt(writeVar, write + actual);

            // Update status
            buffer.putInt(emptyVar, STATUS_UNSET);

            if(write + actual == read) {
                buffer.putInt(fullVar, STATUS_SET);
            }

            return actual;
        }

        int orig = actual;

        avail = read;
        actual = Math.min(length - orig, avail);

        buffer.put(bufOffset, input, offset + orig, actual);
        buffer.putInt(writeVar, actual);

        // Update status
        buffer.putInt(emptyVar, STATUS_UNSET);

        if(actual == read) {
            buffer.putInt(fullVar, STATUS_SET);
        }

        return actual + orig;
    }

    public int read(byte[] output)
    {
        // TODO
        return 0;
    }

    /**
     * Close the underlying file
     * @throws IOException if the close operation fails
     */
    public void close() throws IOException
    {
        file.close();
    }
}
