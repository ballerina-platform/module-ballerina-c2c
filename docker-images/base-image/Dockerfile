# Copyright 2022 WSO2 Inc. (http://wso2.org)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM alpine:3.21.3

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# fontconfig and ttf-dejavu added to support serverside image generation by Java programs
RUN apk upgrade --no-cache \
    && apk add --no-cache fontconfig libretls musl-locales musl-locales-lang ttf-dejavu tzdata zlib libc6-compat gcompat libgcc \
        'libcrypto3>=3.3.7-r1' 'libssl3>=3.3.7-r1' 'libexpat>=2.8.4-r0'

ENV JAVA_VERSION jdk-21.0.12.1+1


RUN set -eux; \
    ARCH="$(apk --print-arch)"; \
    case "${ARCH}" in \
      amd64|x86_64) \
          ESUM='7bc7c2f9ba5ffea5f727e14581964bd54294b8edc2be9a79ea6393f8d0799200'; \
          BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_alpine-linux_hotspot_21.0.12.1_1.tar.gz'; \
         ;; \
      aarch64) \
          ESUM='98e15ae359e1f87160ee4c0abb43336513e41712d439fd4a805a5474af32b2c7'; \
          BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_aarch64_alpine-linux_hotspot_21.0.12.1_1.tar.gz'; \
        ;;\
      *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
      ;; \
    esac; \
	wget -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
	echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
	mkdir -p /opt/java/openjdk; \
	tar --extract \
	    --file /tmp/openjdk.tar.gz \
	    --directory /opt/java/openjdk \
	    --strip-components 1 \
	    --no-same-owner \
	  ; \
    rm -rf /tmp/openjdk.tar.gz;

ENV LD_PRELOAD=/lib/libgcompat.so.0

ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:$PATH"

RUN java --version
