FROM ghcr.io/graalvm/native-image:ol8-java11-22.2.0

RUN microdnf --nodocs install tar gzip make && microdnf clean all && rm -rf /var/cache/yum

ARG RESULT_LIB="/staticlibs"

RUN mkdir ${RESULT_LIB} && cd "${RESULT_LIB}" && \
    curl -L -o x86_64-linux-musl-native.tgz  http://more.musl.cc/10/x86_64-linux-musl/x86_64-linux-musl-native.tgz && \
    tar -xvzf x86_64-linux-musl-native.tgz --strip-components 1
    
ENV CC="${RESULT_LIB}/bin/gcc"

RUN  rm -f ${RESULT_LIB}/x86_64-linux-musl-native.tgz

ENV PATH="$PATH:${RESULT_LIB}/bin"

RUN curl -L -o zlib.tar.gz https://zlib.net/zlib-1.2.12.tar.gz && \
   mkdir zlib && tar -xvf zlib.tar.gz -C zlib --strip-components 1 && cd zlib && \
   ./configure --prefix="${RESULT_LIB}" --static  && \
    make && make install && \
    cd / && rm -rf /zlib && rm -f /zlib.tar.gz

COPY build-native.sh build/
