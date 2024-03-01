/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.network.connectivity;

import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.SocketConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * used to create and receive TLS connections, based on the given trustStore
 */
public class TlsFactory implements SocketFactory {
    private final SocketConfig socketConfig;
    private final SSLServerSocketFactory sslServerSocketFactory;
    private final SSLSocketFactory sslSocketFactory;

    /**
     * Construct this object to create and receive TLS connections. This is done using the trustStore
     * whose reference was passed in as an argument. That trustStore must contain certs for all
     * the members before calling this constructor. This method will then create the appropriate
     * KeyManagerFactory, TrustManagerFactory, SSLContext, SSLServerSocketFactory, and SSLSocketFactory, so
     * that it can later create the TLS sockets.
     */
    public TlsFactory(
            final Certificate agrCert,
            final PrivateKey agrKey,
            final List<PeerInfo> peers,
            @NonNull final SocketConfig socketConfig,
            @NonNull final CryptoConfig cryptoConfig)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException,
                    CertificateException, IOException {
        Objects.requireNonNull(cryptoConfig);

        //final KeyStore signingTrustStore = keysAndCerts.publicStores().sigTrustStore();
        final KeyStore signingTrustStore = CryptoStatic.createPublicKeyStore(peers);

        this.socketConfig = Objects.requireNonNull(socketConfig);

        final char[] password = cryptoConfig.keystorePassword().toCharArray();
        /* nondeterministic CSPRNG */
        final SecureRandom nonDetRandom = CryptoStatic.getNonDetRandom();

        // the agrKeyStore should contain an entry with both agrKeyPair.getPrivate() and agrCert
        // PKCS12 uses file extension .p12 or .pfx
        final KeyStore agrKeyStore = KeyStore.getInstance(CryptoConstants.KEYSTORE_TYPE);
        agrKeyStore.load(null, null); // initialize
        agrKeyStore.setKeyEntry(
                "key", agrKey, password, new Certificate[] {agrCert});

        // "PKIX" may be more interoperable than KeyManagerFactory.getDefaultAlgorithm or
        // TrustManagerFactory.getDefaultAlgorithm(), which was "SunX509" on one system tested
        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(CryptoConstants.KEY_MANAGER_FACTORY_TYPE);
        keyManagerFactory.init(agrKeyStore, password);
        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(CryptoConstants.TRUST_MANAGER_FACTORY_TYPE);
        trustManagerFactory.init(signingTrustStore);
        final SSLContext sslContext = SSLContext.getInstance(CryptoConstants.SSL_VERSION);
        SSLContext.setDefault(sslContext);
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), nonDetRandom);
        sslServerSocketFactory = sslContext.getServerSocketFactory();
        sslSocketFactory = sslContext.getSocketFactory();
    }

    @Override
    public ServerSocket createServerSocket(final int port) throws IOException {
        final SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket();
        serverSocket.setEnabledCipherSuites(new String[] {CryptoConstants.TLS_SUITE});
        serverSocket.setWantClientAuth(true);
        serverSocket.setNeedClientAuth(true);
        SocketFactory.configureAndBind(serverSocket, socketConfig, ALL_INTERFACES, port);
        return serverSocket;
    }

    @Override
    public Socket createClientSocket(final String hostname, final int port) throws IOException {
        final SSLSocket clientSocket = (SSLSocket) sslSocketFactory.createSocket();
        // ensure the connection is ALWAYS the exact cipher suite we've chosen
        clientSocket.setEnabledCipherSuites(new String[] {CryptoConstants.TLS_SUITE});
        clientSocket.setWantClientAuth(true);
        clientSocket.setNeedClientAuth(true);
        SocketFactory.configureAndConnect(clientSocket, socketConfig, hostname, port);
        clientSocket.startHandshake();
        return clientSocket;
    }
}
