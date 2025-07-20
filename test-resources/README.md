# Cetificate setup

some of the tests work with certificates.
They can expire after some years.
To regenerate them:

```
openssl genrsa -out ca_key.pem 4096
openssl req -x509 -key ca_key.pem -out ca_crt.pem -days 3650 -subj "/CN=Puppet CA: localhost" -config extension.cnf -extensions CA_extensions
openssl genrsa -out localhost.key 4096
openssl req -new -key localhost.key -out localhost.csr -subj "/CN=localhost"
openssl x509 -req -in localhost.csr -CA ca.pem -CAkey ca_key.pem -CAcreateserial -out localhost.pem -days 825 -sha256 -extfile <(printf "[v3_req]\nsubjectAltName=DNS:localhost,DNS:puppet") -extensions v3_req
```
