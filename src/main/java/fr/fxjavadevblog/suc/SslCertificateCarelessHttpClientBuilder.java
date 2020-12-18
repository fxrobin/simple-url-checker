package fr.fxjavadevblog.suc;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ssl.TLS;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SslCertificateCarelessHttpClientBuilder {

	public static CloseableHttpClient build() {
		
		TrustManager[] trustAllCerts = new TrustManager[] {

				new X509TrustManager() {

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType)
							throws CertificateException {

					}

					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType)
							throws CertificateException {

					}
				} };
		
		try 
		{
			SSLContext context = SSLContext.getInstance("SSL");
			context.init(null, trustAllCerts, new SecureRandom());

			SSLConnectionSocketFactory socketFactory = SSLConnectionSocketFactoryBuilder
					.create()
					.setSslContext(context)
					.setTlsVersions(TLS.V_1_2)
					.build();
			
			HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder
					.create()
					.setSSLSocketFactory(socketFactory)
					.build();

			return HttpClients
					.custom()
					.setConnectionManager(connectionManager)
					.build();
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			log.error(e.getMessage());
			return HttpClients.createDefault();
		} 
	
	}
}
