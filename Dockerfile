FROM amazonlinux:latest
VOLUME ["/export"]
RUN echo us-west-2 > /etc/yum/vars/awsregion
RUN echo 'proxy=http://192.168.61.8:3128/' >> /etc/yum.conf
RUN yum update -y
RUN yum install -y binutils bzip2-devel curl gcc gcc-c++ gdbm-devel gzip \
  ncurses-devel openssl-devel readline-devel sqlite-devel tar xz-devel zip zlib-devel

RUN mkdir -p /tmp/python-3.6.1-build
WORKDIR /tmp/python-3.6.1-build
RUN curl -o Python-3.6.1.tgz https://d2jkfhwp00dmlu.cloudfront.net/ftp/python/3.6.1/Python-3.6.1.tgz
RUN tar xfz Python-3.6.1.tgz

WORKDIR /tmp/python-3.6.1-build/Python-3.6.1
RUN ./configure --prefix=/usr --with-cxx-main=g++
RUN make -j 32
RUN make install

RUN pip3.6 install virtualenv

RUN mkdir -p /webapp
RUN virtualenv --python=python3.6 /webapp/venv
ENV VIRTUAL_ENV=/webapp/venv
ENV PATH=/webapp/venv/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV LANG=en_US.UTF-8
WORKDIR /webapp
COPY requirements.txt /webapp
RUN pip install -r requirements.txt

COPY tideserver.py /webapp

RUN python3.6 -mpy_compile tideserver.py
RUN zip /lambda.zip tideserver.py
RUN zip -r /lambda.zip __pycache__

WORKDIR /webapp/venv/lib/python3.6/site-packages
RUN zip -r /lambda.zip . -x "*.dist-info" "*.dist-info/*" "boto3" "boto3/*" \
  "botocore" "botocore/*" "docutils" "docutils/*" "jmespath" "jmespath/*" \
  "pip" "pip/*" "pylint" "pylint/*" "setuptools" "setuptools/*" \
  "wheel" "wheel/*" > /dev/null
