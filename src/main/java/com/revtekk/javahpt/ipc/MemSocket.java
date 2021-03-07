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
 * |-------------------|------------------------------------|-----------------------------------|
 * | Control Variables | Server ---> Client Circular Buffer | Client --> Server Circular Buffer |
 * |-------------------|------------------------------------|-----------------------------------|
 *
 * The control variables section at the very front of the memory-mapped file
 * contains the read and write positions for the server and client for each of
 * the circular buffers. This allows each side to know:
 *
 *   1. How much space is left in the buffer
 *   2. Whether there is additional data to read
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
 */
public class MemSocket
{
    private RandomAccessFile file;
    private MappedByteBuffer buffer;

    private boolean server;
    private int stoCBufSize, ctoSBufSize;

    // Control variable positions
    private static final int SERVER_READ = 0, SERVER_WRITE = 4,
            CLIENT_READ = 8, CLIENT_WRITE = 12;

    // Start of the first buffer -- always at position 16 within the memory mapped file, since
    // it always immediately follows the control variables
    private static final int FIRST_START = 16;

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
        long size = FIRST_START + stoCBufSize + ctoSBufSize;

        this.stoCBufSize = stoCBufSize;
        this.ctoSBufSize = ctoSBufSize;

        this.buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
        this.server = server;

        if(server) {
            buffer.putInt(SERVER_READ, stoCBufSize);
            buffer.putInt(SERVER_WRITE, 0);
        } else {
            buffer.putInt(CLIENT_READ, 0);
            buffer.putInt(CLIENT_WRITE, stoCBufSize);
        }
    }

    public int write(byte[] input)
    {
        // TODO
        return 0;
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
