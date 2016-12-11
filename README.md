JDrupes non-blocking HTTP Codec
===============================

[![Build Status](https://travis-ci.org/mnlipp/org.jdrupes.httpcodec.svg?branch=master)](https://travis-ci.org/mnlipp/org.jdrupes.httpcodec) 
[![Release](https://jitpack.io/v/mnlipp/org.jdrupes.httpcodec.svg)](https://jitpack.io/mnlipp/org.jdrupes.httpcodec)


The goal of this package is to provide easy to use HTTP 
encoders and decoders for non-blocking I/O
that use Java `Buffer`s for handling the data.

I'm well aware that such libraries already exist (searching easily reveals
implementations such as the 
[Apache Codecs](https://hc.apache.org/httpcomponents-core-ga/httpcore-nio/apidocs/org/apache/http/impl/nio/codecs/package-summary.html) 
or the 
[Netty Codes](http://netty.io/4.0/api/io/netty/handler/codec/http/package-summary.html)).
However, I found all of them to be too closely integrated with their respective
frameworks, which didn't go well with my intention to write my own  
[event driven framework](http://mnlipp.github.io/jgrapes/). 
An implementation that comes very close to what I needed is 
[HTTP Kit](https://github.com/http-kit/http-kit), which has, however,
dependencies on Clojure, that prohibit its usage for my purpose.

This library can be used with Java 8 SE. It has no further dependencies.
Binaries can be accessed using [JitPack](https://jitpack.io/). User is
`com.github.mnlipp`, Repo is `org.jdrupes.httpcodec` and currently the only 
supported Tag is `master-SNAPSHOT`.

I plan to improve documentation over time. For now, the best starting
point is to have a look at the 
[JavaDoc](https://mnlipp.github.io/org.jdrupes.httpcodec/javadoc/index.html) 
and at the source code in the `demo` folder.

Contributions and bug reports are welcome. Please provide them as
GitHub issues.