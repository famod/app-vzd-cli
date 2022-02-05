package vzd.tools.teststuite

import de.gematik.pki.certificate.Admission
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.util.ASN1Dump
import org.bouncycastle.util.encoders.Base64
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals


class CertTest {
    @Test fun testCertificateParsing() {
        val certData = "MIIEFDCCA7qgAwIBAgICAiUwCgYIKoZIzj0EAwIwgbgxCzAJBgNVBAYTAkRFMTwwOgYDVQQKDDNEZXV0c2NoZSBUZWxla29tIFNlY3VyaXR5IEdtYkggLSBHMiBMb3MgMyBOT1QtVkFMSUQxSDBGBgNVBAsMP0luc3RpdHV0aW9uIGRlcyBHZXN1bmRoZWl0c3dlc2Vucy1DQSBkZXIgVGVsZW1hdGlraW5mcmFzdHJ1a3R1cjEhMB8GA1UEAwwYVFNZU0kuU01DQi1DQTQgVEVTVC1PTkxZMB4XDTIxMDgwNjAwMDAwMFoXDTI2MDgwNTIzNTk1OVowUzELMAkGA1UEBhMCREUxHjAcBgNVBAoMFTcyMjEwNzI5NjY3IE5PVC1WQUxJRDEkMCIGA1UEAwwbVGVzdHByYXhpcyBVUyA2NjcgVEVTVC1PTkxZMFowFAYHKoZIzj0CAQYJKyQDAwIIAQEHA0IABAqMmwFbYGkRflbzSwZs2pLvm1IHrcipazBr0VoiZJ8KlOv4nMdwjU7KdEI9kf/DS76mOMeZ27kQa7t73TIQ7CejggIVMIICETAfBgNVHSMEGDAWgBQzr6SrYZv1YujiKazWhuuvslV7bTAdBgNVHQ4EFgQUAv5AzXnb0QckAFHKRgh0JYaUM9wwDgYDVR0PAQH/BAQDAgMIMCAGA1UdIAQZMBcwCgYIKoIUAEwEgSMwCQYHKoIUAEwETDAMBgNVHRMBAf8EAjAAMIIBAAYDVR0fBIH4MIH1MIHyoIHvoIHshoHpbGRhcDovL2xkYXAuaGJhLnRlc3QudGVsZXNlYy5kZS9DTj1UU1lTSS5TTUNCLUNBNCUyMFRFU1QtT05MWSxPVT1JbnN0aXR1dGlvbiUyMGRlcyUyMEdlc3VuZGhlaXRzd2VzZW5zLUNBJTIwZGVyJTIwVGVsZW1hdGlraW5mcmFzdHJ1a3R1cixPPVQtU3lzdGVtcyUyMEludGVybmF0aW9uYWwlMjBHbWJIJTIwLSUyMEcyJTIwTG9zJTIwMyUyME5PVC1WQUxJRCxDPURFP0NlcnRpZmljYXRlUmV2b2NhdGlvbkxpc3QwRwYFKyQIAwMEPjA8MDowODA2MDQwFgwUQmV0cmllYnNzdMOkdHRlIEFyenQwCQYHKoIUAEwEMhMPMS0yMDcyMjEwNzI5NjY3MEIGCCsGAQUFBwEBBDYwNDAyBggrBgEFBQcwAYYmaHR0cDovL29jc3Auc21jYi50ZXN0LnRlbGVzZWMuZGUvb2NzcHIwCgYIKoZIzj0EAwIDSAAwRQIhAKiXAStZzq+rQnCzYYdAFkNWooHx7dI6dtDQXjQrD68BAiBTg0tAa7ziCe+jFjuRjOgQswfREOThAB2mrOMfHRTvCQ=="
        val bytes = Base64.decode(certData)

        val cf = CertificateFactory.getInstance("X.509")
        val cert: X509Certificate = cf.generateCertificate(bytes.inputStream()) as X509Certificate

        assertEquals("CN=Testpraxis US 667 TEST-ONLY, O=72210729667 NOT-VALID, C=DE", cert.subjectDN.name)

        val reencodedCertData = Base64.toBase64String(cert.encoded)

        assertEquals(certData, reencodedCertData)

    }

}