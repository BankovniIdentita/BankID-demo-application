# BankID demo application

This project is intended as an example of a complete application with integrated authentication via Banking Identity.
The same application works at [https://demo.bankid.cz](https://demo.bankid.cz) and allows the end-user to try logging in via a bank identity without obligation.

## Features implemented in the demo:
- invoking the authentication flow
- token exchange after successful user authentication
- retrieval and display of user data (within the scope of the Connect service)
- user logout from BankID (token revocation)\
- signing of example document

## What do I need to run the application:
**1. Setting credentials**
   
   For the application to work with BankID, it is necessary to have a registered application in the [Developer Portal](https://developer.bankid.cz). Registration in the portal is freely available. It is possible to use the Sandbox environment for implementation tests.

**Parameters I will need:**

- **client_id** - generated on the developer portal
- **client_secret** - generated on the developer portal
- **redirect_uri** - if I run the application from the local computer, eg ```http://localhost:8080/callback``` otherwise https://{my-domain-and-uri}/callback
- **issuer_uri** - it is listed in the developer portal next to the application configuration (for Sandbox it is currently https://oidc.sandbox.bankid.cz/)
- **keystore** - application needs valid keystore in form of PKCS12. Keystore must contain elliptic curve key with alias ```rp-sign``` used for signing and RSA key with alias ```rp-encrypt```used for encryption. Keystore can use loaded from classpath or from filesystem. For creation of keys, keytool utility can be used. Make sure you use correct version of Java.  


These parameters are entered as values in ```application.yml```

> When configuring the application in the Developer Portal, make sure you have the following scopes set: (```openid``` ```profile.name``` ```profile.email``` ```profile.phonenumber```)
> 
> And that you have set ```redirect_uri``` correctly.
 
You can generate adequate keys for keystore by following commands.
> keytool -genkeypair -keyalg EC -keysize 521 -sigalg SHA512withECDSA -alias rp-sign -validity 365 -dname "CN=Name,OU=IT,O=Test,L=Solutions,C=CZ" -deststoretype pkcs12 -keystore keystore.jks -storepass change-it
> 
> keytool -genkey -alias rp-encrypt -keystore keystore.jks -keyalg RSA -storepass change-it -validity 365 -keysize 2048 -dname "CN=Name,OU=IT,O=Test,L=Solutions,C=CZ"

Please note that keytool should be in same version as Java runtime of application (i.e. keystore created by keytool from Java 16 won't work with Java 11 JRE) 

**2. Launch the application**

   You can use docker to run the application:

Build
```
docker build -t bankiddemo .
```

Run
```
docker run -p 8080:8080 bankiddemo
```

And test in browser
```shell
http://localhost:8080
```

