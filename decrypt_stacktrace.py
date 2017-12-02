#!/usr/bin/env python3
from base64 import b64decode
import hashlib
from os import environ
from sys import stdin, stdout
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.ciphers import Cipher
from cryptography.hazmat.primitives.ciphers.algorithms import AES
from cryptography.hazmat.primitives.ciphers.modes import CBC
from cryptography.hazmat.primitives.padding import PKCS7

key_string = environ.get("STACK_TRACE_KEY")
if not key_string:
    print("Environment variable STACK_TRACE_KEY must be set.")
    exit(1)

hasher = hashlib.new("sha256")
hasher.update(key_string.encode("utf-8"))
key = hasher.digest()[:16]

packet_b64 = ""
while True:
    data = stdin.read()
    if not data:
        break

    packet_b64 += data

packet = b64decode(packet_b64)
iv = packet[:16]
ciphertext = packet[16:]

cipher = Cipher(AES(key), CBC(iv), default_backend())
decryptor = cipher.decryptor()
decrypted_padded = decryptor.update(ciphertext)
decrypted_padded += decryptor.finalize()

unpadder = PKCS7(128).unpadder()
stdout.write(unpadder.update(decrypted_padded).decode("utf-8"))
stdout.write(unpadder.finalize().decode("utf-8"))
stdout.flush()
