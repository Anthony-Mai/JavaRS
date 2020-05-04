# JavaGS

This is an adaptation of Genesis Code, a new generation erasure code
technology developed by Genesis Codes inventor Anthony Mai, into the
JavaReedSolomon, open source project of Backblaze's original simple
and efficient Reed-Solomon implementation in Java, which was originally
built at [Backblaze](https://www.backblaze.com).
There is an overview of how the Reed Solomon algorithm works in their
[blog post](https://www.backblaze.com/blog/reed-solomon/).

Reed Solomon Code, invented in 1960 and refined over the years, is
slow and inefficient, with computation complexity increases in the
quadratic power of code length. Genesis Codes is a superior replacement
with complexity scales only linearly, or even su-linearly in some cases.

The ReedSolomon class does the encoding and decoding, and is supported
by Matrix, which does matrix arithmetic, and Galois, which is a finite
field over 8-bit values.

For examples of how to use ReedSolomon, take a look at SampleEncoder
and SampleDecoder.  They show, in a very simple way, how to break a
file into shards and encode parity, and then how to take a subset of
the shards and reconstruct the original file.

There is a Gradle build file to make a jar and run the tests.  Running
it is simple.  At the root folder just type: gradle build.

Then you need to go to the newly created build folder and put the JNI
component of Genesis Codes, named libgenesis.so. You will have to get
the file from Anthony Mai, who invented Genesis Codes. I cannot make
the JNI component available publicly because it is my trade secret.
I can be reached at mai_anthony@hotmail.com or find me at LinkedIn.

To try out Genesis encoding/decoding, type this command under build:
java -Djava.library.path=. -cp ./classes/java/main com/genesis/GenesisEncoder somefile.txt

I would also like to send out a special thanks to James Plank at the
University of Tennessee at Knoxville for his useful papers on erasure
coding.  If you'd like an intro into how it all works, take a look at
[this introductory paper](http://web.eecs.utk.edu/~plank/plank/papers/SPE-9-97.html).

This project is meant to demonstrate usage of Genesis Code in Java.
If you need more speed, consult the Genesis Code inventor for help.
You may be interested in using the Intel SIMD instructions to speed
up the Galois field multiplication.  You can read more about that 
in the paper on [Screaming Fast Galois Field Arithmetic](http://www.kaymgee.com/Kevin_Greenan/Publications_files/plank-fast2013.pdf).

## Performance Notes

The following are original performance notes of Reed Solomon Code.
On initial evaluation, Genesis Codes delivers multi-GB per seconds
throughput on the same platform, and is up to a hundred times faster.

The performance of the inner loop depends on the specific processor
you're running on.  There are twelve different permutations of the
loop in this library, and the ReedSolomonBenchmark class will tell
you which one is faster for your particular application.  The number
of parity and data shards in the benchmark, as well as the buffer
sizes, match the usage at Backblaze.  You can set the parameters of
the benchmark to match your specific use before choosing a loop
implementation. 

These are the speeds I got running the benchmark on a Backblaze
storage pod:

```
    ByteInputOutputExpCodingLoop         95.2 MB/s
    ByteInputOutputTableCodingLoop      107.0 MB/s
    ByteOutputInputExpCodingLoop        130.3 MB/s
    ByteOutputInputTableCodingLoop      181.4 MB/s
    InputByteOutputExpCodingLoop         94.4 MB/s
    InputByteOutputTableCodingLoop      138.3 MB/s
    InputOutputByteExpCodingLoop        200.4 MB/s
    InputOutputByteTableCodingLoop      525.7 MB/s
    OutputByteInputExpCodingLoop        143.7 MB/s
    OutputByteInputTableCodingLoop      209.5 MB/s
    OutputInputByteExpCodingLoop        217.6 MB/s
    OutputInputByteTableCodingLoop      515.7 MB/s
```

![Bar Chart of Benchmark Results](notes/benchmark_on_storage_pod.png)
