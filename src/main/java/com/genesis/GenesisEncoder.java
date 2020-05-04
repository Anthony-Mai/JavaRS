/**
 * Command-line program encodes one file using Genesis Codes 16+3.
 *
 * Copyright 2020, Genesis Codes, Inc.
 */


package com.genesis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

class Genesis {
    static {
        System.loadLibrary("genesis");
    }

    public native static void encodeParity(byte [][] datas, byte [][] parity, int mask);
};


/**
 * Command-line program encodes one file using Reed-Solomon 4+2.
 *
 * The one argument should be a file name, say "foo.txt". This program
 * will create 16 files in the same directory, breaking the input file
 * into 16 data shards, and two parity shards.  The output files are
 * called "foo.txt.D00", "foo.txt.D01", ..., and "foo.txt.P0". The 'P'
*  and P in the suffix means data shard and parity shard respectively..
 *
 * The data stored is file content, immediately followed by filename.
 * The last 4 bytes in the last data shard if the offset of filename,
 * counting forward from the end of the shard. Between the file name
 * and the final offset value is padded by 0 bytes. so all the data
 * shards have the same size, which is number of strips times 384.
 */

public class GenesisEncoder {

    public static final int DATA_SHARDS = 16;
    public static final int CODE_SHARDS = 3;

    public static final int BYTES_IN_INT = 4;

    public static void main(String [] arguments) throws IOException {

        // Parse the command line
        if (arguments.length != 1) {
            System.out.println("Usage: SampleEncoder <fileName>");
            return;
        }
        final String fileName = arguments[0];
        final File inputFile = new File(fileName);
        int fileSize = 0;
        if (!inputFile.exists()) {
            System.out.println("Cannot read input file: " + inputFile);
        } else {
            fileSize = (int) inputFile.length();
        }

        // The 32 bits mask tells what kind of processing is needed.
        // Bit 31: Always 0, not currently used.
        // Bit 29-30: Number of the parity blocks, supports 1 to 3.
        // Bit 24-28: Number of data chards, supports 1 to 20.
        // Bit 20-24: Each set bit means a parity shard to created.
        // Bit  0-19: Each set bit means a data shard to recover.
        int   mask = (CODE_SHARDS<<29)|(DATA_SHARDS<<24)|(0<<20)|(0<<0);

        // Get the size of the input file.  (Files bigger that
        // Integer.MAX_VALUE will fail here!)
        final int dataSize = 16*24;
        final int codeSize = 16*25;
        final int stripSize = dataSize * DATA_SHARDS;

        // Figure out how big each shard will be.  The total size stored
        // will be the file size (8 bytes) plus the file.
        int numStrips = (fileSize>0)? (fileSize + fileName.length() + BYTES_IN_INT + stripSize)/stripSize : 0;
        int shardSize = numStrips * dataSize;
        int storedSize = shardSize * DATA_SHARDS;

        // Create a buffer holding the file size, followed by
        // the contents of the file.
        if (fileSize == 0) {
            for (int i = 0; i < CODE_SHARDS; i++) {
                if (numStrips > 0) break;
                File inCodeFile = new File(fileName + ".P" + i);
                if (!inCodeFile.exists()) continue;
                numStrips = ((int)inCodeFile.length()) / codeSize;
            }
            shardSize = numStrips * dataSize;
            storedSize = shardSize * DATA_SHARDS;
        }

        final byte [] allBytes = new byte[storedSize];

        if (fileSize > 0) {
            InputStream in = new FileInputStream(inputFile);
            int bytesRead = in.read(allBytes, 0, fileSize);
            if (bytesRead != fileSize) {
                throw new IOException("not enough bytes read");
            }
            in.close();

            byte[] strArray = fileName.getBytes();
            for (int i = 0; i < strArray.length; i++) allBytes[fileSize+i] = strArray[i];

            allBytes[fileSize+strArray.length] = 0x00;
            allBytes[storedSize-4] = (byte)(storedSize-fileSize);
            allBytes[storedSize-3] = (byte)((int)(storedSize-fileSize)>>8);
            allBytes[storedSize-2] = (byte)((int)(storedSize-fileSize)>>16);
            allBytes[storedSize-1] = (byte)((int)(storedSize-fileSize)>>24);
        }

        // Make the buffers to hold the shards.
        byte [][] datas = new byte [DATA_SHARDS][numStrips*dataSize];
        byte [][] codes = new byte [CODE_SHARDS][numStrips*codeSize];

        for (int i = 0; i < CODE_SHARDS; i++) {
            File inCodeFile = new File(fileName + ".P" + i);
            if (inCodeFile.exists()) {
                InputStream in = new FileInputStream(inCodeFile);
                in.read(codes[i], 0, codes[i].length);
            } else {
                mask |= 1<<(20+i);
            }
        }
        // Fill in the data shards
        for (int i = 0; i < DATA_SHARDS; i++) {
            if ((mask & (7<<20)) == (7<<20)) {
                // No parity shards exist. We obviously are doing encoding.
                System.arraycopy(allBytes, i * shardSize, datas[i], 0, shardSize);
            } else {
                File inDataFile = new File(fileName + ((i>9)?".D":".D0") + i);
                if (inDataFile.exists()) {
                    // TODO: Examine the checksunm to ensure file is not corrupted.
                    InputStream in = new FileInputStream(inDataFile);
                    in.read(datas[i], 0, datas[i].length);
                } else {
                    mask |= 1<<i;
                }
            }
        }

        Genesis.encodeParity(datas, codes, mask);

        //We could have used Reed-Solomon to calculate the parity.
        //ReedSolomon reedSolomon = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS);
        //reedSolomon.encodeParity(shards, 0, shardSize);

        // Write out the resulting files.
        for (int i = 0; i < DATA_SHARDS; i++) {
            if (!((fileSize > 0) || ((mask & (1<<i)) > 0))) continue;
            File outputFile = new File(
                    inputFile.getParentFile(),
                    inputFile.getName() + ((i>9)?".D":".D0") + i);
            OutputStream out = new FileOutputStream(outputFile);
            out.write(datas[i]);
            out.close();
            System.out.println("Wrote " + outputFile);
        }
        for (int i = 0; i < CODE_SHARDS; i++) {
            if (0 == (mask & (1<<(20+i)))) continue;
            File outputFile = new File(
                    inputFile.getParentFile(),
                    inputFile.getName() + ".P" + i);
            OutputStream out = new FileOutputStream(outputFile);
            out.write(codes[i]);
            out.close();
            System.out.println("Wrote " + outputFile);
        }
        if (((fileSize == 0) | ((mask&0xFFFFFF) > 0)) && (numStrips > 0)) {
            for (int i = 0; i < DATA_SHARDS; i++) {
                // No parity shards exist. We obviously are doing encoding.
                System.arraycopy(datas[i], 0, allBytes, i * shardSize, shardSize);
            }
            int offset = allBytes[storedSize-1];
            offset <<= 8; offset += allBytes[storedSize-2];
            offset <<= 8; offset += allBytes[storedSize-3];
            offset <<= 8; offset += allBytes[storedSize-4];
            fileSize = storedSize - offset;
            File outputFile = new File(fileName + ".new");
            OutputStream out = new FileOutputStream(outputFile);
            out.write(allBytes, 0, fileSize);
            out.close();
            System.out.println("Wrote " + outputFile);
        }
    }
}
