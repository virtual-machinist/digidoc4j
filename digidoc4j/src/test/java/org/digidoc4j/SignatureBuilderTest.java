/* DigiDoc4J library
 *
 * This software is released under either the GNU Library General Public
 * License (see LICENSE.LGPL).
 *
 * Note that the only valid version of the LGPL license as far as this
 * project is concerned is the original GNU Library General Public License
 * Version 2.1, February 1999
 */

package org.digidoc4j;

import eu.europa.esig.dss.validation.timestamp.TimestampToken;
import eu.europa.esig.dss.validation.SignaturePolicy;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.digidoc4j.exceptions.IllegalSignatureProfileException;
import org.digidoc4j.exceptions.ServiceUnreachableException;
import org.digidoc4j.exceptions.InvalidSignatureException;
import org.digidoc4j.exceptions.NotSupportedException;
import org.digidoc4j.exceptions.SignatureTokenMissingException;
import org.digidoc4j.impl.asic.asice.AsicESignature;
import org.digidoc4j.impl.asic.asice.bdoc.BDocContainerBuilder;
import org.digidoc4j.impl.asic.asice.bdoc.BDocSignature;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.digidoc4j.test.CustomContainer;
import org.digidoc4j.test.MockSignatureBuilder;
import org.digidoc4j.test.TestAssert;
import org.digidoc4j.test.util.TestDataBuilderUtil;
import org.digidoc4j.test.util.TestSigningUtil;
import org.digidoc4j.utils.TokenAlgorithmSupport;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Date;
import java.util.List;

import static org.digidoc4j.Configuration.Mode.TEST;
import static org.digidoc4j.Container.DocumentType.ASICE;
import static org.digidoc4j.Container.DocumentType.BDOC;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SignatureBuilderTest extends AbstractTest {

  @Test
  public void buildingDataToSign_shouldReturnDataToSign() throws Exception {
    Container container = this.createNonEmptyContainer();
    SignatureBuilder builder = SignatureBuilder.aSignature(container).
        withSigningCertificate(pkcs12SignatureToken.getCertificate());
    DataToSign dataToSign = builder.buildDataToSign();
    Assert.assertNotNull(dataToSign);
    Assert.assertNotNull(dataToSign.getDataToSign());
    Assert.assertNotNull(dataToSign.getSignatureParameters());
    Assert.assertEquals(DigestAlgorithm.SHA256, dataToSign.getDigestAlgorithm());
  }

  @Test
  public void buildingDataToSign_shouldContainSignatureParameters() throws Exception {
    Container container = this.createNonEmptyContainer();
    SignatureBuilder builder = SignatureBuilder.aSignature(container).withCity("San Pedro").
        withStateOrProvince("Puerto Vallarta").withPostalCode("13456").withCountry("Val Verde").
        withRoles("Manager", "Suspicious Fisherman").withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT_TM).withSignatureId("S0").
        withSigningCertificate(pkcs12SignatureToken.getCertificate());
    DataToSign dataToSign = builder.buildDataToSign();
    SignatureParameters parameters = dataToSign.getSignatureParameters();
    Assert.assertEquals("San Pedro", parameters.getCity());
    Assert.assertEquals("Puerto Vallarta", parameters.getStateOrProvince());
    Assert.assertEquals("13456", parameters.getPostalCode());
    Assert.assertEquals("Val Verde", parameters.getCountry());
    Assert.assertEquals("Manager", parameters.getRoles().get(0));
    Assert.assertEquals(DigestAlgorithm.SHA256, parameters.getSignatureDigestAlgorithm());
    Assert.assertEquals(SignatureProfile.LT_TM, parameters.getSignatureProfile());
    Assert.assertEquals("S0", parameters.getSignatureId());
    Assert.assertSame(pkcs12SignatureToken.getCertificate(), parameters.getSigningCertificate());
    byte[] bytesToSign = dataToSign.getDataToSign();
    Assert.assertNotNull(bytesToSign);
    Assert.assertTrue(bytesToSign.length > 1);
  }

  @Test
  public void signDocumentExternallyTwice() throws Exception {
    Container container = this.createNonEmptyContainer();
    DataToSign dataToSign = TestDataBuilderUtil.buildDataToSign(container, "S0");
    Signature signature = TestDataBuilderUtil.makeSignature(container, dataToSign);
    this.assertSignatureIsValid(signature, SignatureProfile.LT);
    DataToSign dataToSign2 = TestDataBuilderUtil.buildDataToSign(container, "S1");
    Signature signature2 = TestDataBuilderUtil.makeSignature(container, dataToSign2);
    this.assertSignatureIsValid(signature2, SignatureProfile.LT);
    container.saveAsFile(this.getFileBy("asice"));
  }

  @Test
  public void signContainerWithSignatureToken() throws Exception {
    Container container = this.createNonEmptyContainer();
    Signature signature = SignatureBuilder.aSignature(container).withCity("Tallinn").
        withStateOrProvince("Harjumaa").withPostalCode("13456").withCountry("Estonia").
        withRoles("Manager", "Suspicious Fisherman").withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT_TM).withSignatureToken(pkcs12SignatureToken).invokeSigning();
    container.addSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
    container.saveAsFile(this.getFileBy("bdoc"));
    this.assertSignatureIsValid(signature, SignatureProfile.LT_TM);
    Assert.assertEquals("Tallinn", signature.getCity());
    Assert.assertEquals("Harjumaa", signature.getStateOrProvince());
    Assert.assertEquals("13456", signature.getPostalCode());
    Assert.assertEquals("Estonia", signature.getCountryName());
    Assert.assertEquals(2, signature.getSignerRoles().size());
    Assert.assertEquals("Manager", signature.getSignerRoles().get(0));
    Assert.assertEquals("Suspicious Fisherman", signature.getSignerRoles().get(1));
  }

  @Test
  public void createTimeMarkSignature_shouldNotContainTimestamp() throws Exception {
    Container container = this.createNonEmptyContainer();
    BDocSignature signature = (BDocSignature) SignatureBuilder.aSignature(container).
        withSignatureProfile(SignatureProfile.LT_TM).withSignatureToken(pkcs12SignatureToken).invokeSigning();
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    List<TimestampToken> signatureTimestamps = signature.getOrigin().getDssSignature().getSignatureTimestamps();
    Assert.assertTrue(signatureTimestamps == null || signatureTimestamps.isEmpty());
  }

  @Test(expected = SignatureTokenMissingException.class)
  public void signContainerWithMissingSignatureToken_shouldThrowException() throws Exception {
    Container container = this.createNonEmptyContainer();
    SignatureBuilder.aSignature(container).invokeSigning();
  }

  @Test
  public void signatureProfileShouldBeSetProperlyForBDoc() throws Exception {
    Signature signature = createBDocSignatureWithProfile(SignatureProfile.B_BES);
    Assert.assertEquals(SignatureProfile.B_BES, signature.getProfile());
    Assert.assertTrue(signature.getSignerRoles().isEmpty());
  }

  @Test
  public void signatureProfileShouldBeSetProperlyForBDocTS() throws Exception {
    Signature signature = createBDocSignatureWithProfile(SignatureProfile.LT);
    Assert.assertEquals(SignatureProfile.LT, signature.getProfile());
  }

  @Test
  public void signatureProfileShouldBeSetProperlyForBDocTM() throws Exception {
    Signature signature = createBDocSignatureWithProfile(SignatureProfile.LT_TM);
    Assert.assertEquals(SignatureProfile.LT_TM, signature.getProfile());
  }

  @Test
  public void signatureProfileShouldBeSetProperlyForBEpes() throws Exception {
    Signature signature = createBDocSignatureWithProfile(SignatureProfile.B_EPES);
    Assert.assertEquals(SignatureProfile.B_EPES, signature.getProfile());
    Assert.assertNull(signature.getTrustedSigningTime());
    Assert.assertNull(signature.getOCSPCertificate());
    Assert.assertNull(signature.getOCSPResponseCreationTime());
    Assert.assertNull(signature.getTimeStampTokenCertificate());
    Assert.assertNull(signature.getTimeStampCreationTime());
    AsicESignature bDocSignature = (AsicESignature) signature;
    SignaturePolicy policyId = bDocSignature.getOrigin().getDssSignature().getSignaturePolicy();
    Assert.assertEquals("1.3.6.1.4.1.10015.1000.3.2.1", policyId.getIdentifier());
  }

  @Test
  public void signWithEccCertificate() throws Exception {
    Container container = this.createNonEmptyContainer();
    Signature signature = SignatureBuilder.aSignature(container).
        withSignatureToken(new PKCS12SignatureToken("src/test/resources/testFiles/p12/MadDogOY.p12", "test".toCharArray())).
        withEncryptionAlgorithm(EncryptionAlgorithm.ECDSA).invokeSigning();
    Assert.assertTrue(signature.validateSignature().isValid());
    Assert.assertEquals(SignatureProfile.LT, signature.getProfile());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
  }

  @Test
  public void signWith2EccCertificate() throws Exception {
    Container container = this.createNonEmptyContainer();
    Signature signature = SignatureBuilder.aSignature(container).withSignatureToken(pkcs12EccSignatureToken).
        withEncryptionAlgorithm(EncryptionAlgorithm.ECDSA).withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT_TM).invokeSigning();
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    signature = SignatureBuilder.aSignature(container).
        withSignatureToken(new PKCS12SignatureToken("src/test/resources/testFiles/p12/MadDogOY.p12", "test".toCharArray())).
        withEncryptionAlgorithm(EncryptionAlgorithm.RSA).withSignatureProfile(SignatureProfile.LT).invokeSigning();
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
  }

  @Test
  public void signTMWithEccCertificate() throws Exception {
    Container container = this.createNonEmptyContainer();
    Signature signature = SignatureBuilder.aSignature(container).
        withSignatureToken(new PKCS12SignatureToken("src/test/resources/testFiles/p12/MadDogOY.p12", "test".toCharArray())).
        withEncryptionAlgorithm(EncryptionAlgorithm.ECDSA).withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT_TM).invokeSigning();
    Assert.assertNotNull(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
  }

  @Test
  public void signWithEccCertificate_determiningEncryptionAlgorithmAutomatically() throws Exception {
    Container container = this.createNonEmptyContainer();
    Signature signature = this.createSignatureBy(container, new PKCS12SignatureToken("src/test/resources/testFiles/p12/MadDogOY.p12", "test".toCharArray()));
    Assert.assertNotNull(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
  }

  @Test
  public void signWithDeterminedSignatureDigestAlgorithm() throws Exception {
    Container container = this.createNonEmptyContainer();
    DigestAlgorithm digestAlgorithm = TokenAlgorithmSupport.determineSignatureDigestAlgorithm(pkcs12SignatureToken.getCertificate());
    DataToSign dataToSign = SignatureBuilder.aSignature(container).
        withSignatureDigestAlgorithm(digestAlgorithm).withSigningCertificate(pkcs12SignatureToken.getCertificate()).
        buildDataToSign();
    SignatureParameters signatureParameters = dataToSign.getSignatureParameters();
    Assert.assertEquals(DigestAlgorithm.SHA256, signatureParameters.getSignatureDigestAlgorithm());
    Signature signature = TestDataBuilderUtil.makeSignature(container, dataToSign);
    Assert.assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", signature.getSignatureMethod());
    Assert.assertTrue(container.validate().isValid());
  }

  @Test(expected = InvalidSignatureException.class)
  public void openSignatureFromNull_shouldThrowException() throws Exception {
    SignatureBuilder.aSignature(this.createNonEmptyContainerBy(Paths.get("src/test/resources/testFiles/helper-files/test.txt"))).
        openAdESSignature(null);
  }

  @Test
  public void openSignatureFromExistingSignatureDocument() throws Exception {
    Container container = this.createNonEmptyContainerBy(Paths.get("src/test/resources/testFiles/helper-files/test.txt"));
    Signature signature = this.openSignatureFromExistingSignatureDocument(container);
    Assert.assertTrue(signature.validateSignature().isValid());
  }

  @Test(expected = NotSupportedException.class)
  public void SignatureBuilderWithDDoc_throwsException() throws Exception {
    Container container = ContainerOpener.open("src/test/resources/testFiles/valid-containers/ddoc_for_testing.ddoc");
    SignatureBuilder.aSignature(container).buildDataToSign();
  }

  @Test(expected = InvalidSignatureException.class)
  public void openSignatureFromInvalidSignatureDocument() throws Exception {
    Container container = this.createNonEmptyContainerBy(Paths.get("src/test/resources/testFiles/helper-files/test.txt"));
    byte[] signatureBytes = FileUtils.readFileToByteArray(new File("src/test/resources/testFiles/helper-files/test.txt"));
    SignatureBuilder.aSignature(container).openAdESSignature(signatureBytes);
  }

  @Test
  public void openSignature_withDataFilesMismatch_shouldBeInvalid() throws Exception {
    Container container = this.createNonEmptyContainerBy(Paths.get("src/test/resources/testFiles/helper-files/word_file.docx"));
    Signature signature = this.openAdESSignature(container);
    ValidationResult result = signature.validateSignature();
    Assert.assertFalse(result.isValid());
    TestAssert.assertContainsErrors(result.getErrors(),
            "The reference data object has not been found!"
    );
  }

  @Test
  public void openXadesSignature_withoutXmlPreamble_shouldNotThrowException() throws Exception {
    Container container = this.createNonEmptyContainerBy(Paths.get("src/test/resources/testFiles/helper-files/test.txt"));
    byte[] signatureBytes = FileUtils.readFileToByteArray(new File("src/test/resources/testFiles/xades/bdoc-tm-jdigidoc-mobile-id.xml"));
    SignatureBuilder.aSignature(container).openAdESSignature(signatureBytes);
  }

  @Test
  public void openXadesSignature_andSavingContainer_shouldNotChangeSignature() throws Exception {
    Container container = TestDataBuilderUtil.createContainerWithFile("src/test/resources/testFiles/helper-files/word_file.docx");
    Signature signature = this.openAdESSignature(container);
    container.addSignature(signature);
    String file = this.getFileBy("bdoc");
    container.saveAsFile(file);
    container = ContainerOpener.open(file);
    byte[] originalSignatureBytes = FileUtils.readFileToByteArray(new File("src/test/resources/testFiles/xades/valid-bdoc-tm.xml"));
    byte[] signatureBytes = container.getSignatures().get(0).getAdESSignature();
    Assert.assertArrayEquals(originalSignatureBytes, signatureBytes);
  }

  @Test(expected = NotSupportedException.class)
  public void signUnknownContainerFormat_shouldThrowException() throws Exception {
    ContainerBuilder.setContainerImplementation("TEST-FORMAT", CustomContainer.class);
    Container container = TestDataBuilderUtil.createContainerWithFile(this.testFolder, "TEST-FORMAT");
    TestDataBuilderUtil.buildDataToSign(container);
  }

  @Test
  public void signCustomContainer() throws Exception {
    ContainerBuilder.setContainerImplementation("TEST-FORMAT", CustomContainer.class);
    SignatureBuilder.setSignatureBuilderForContainerType("TEST-FORMAT", MockSignatureBuilder.class);
    Container container = TestDataBuilderUtil.createContainerWithFile(testFolder, "TEST-FORMAT");
    DataToSign dataToSign = TestDataBuilderUtil.buildDataToSign(container);
    Assert.assertNotNull(dataToSign);
    byte[] signatureValue = TestSigningUtil.sign(dataToSign.getDataToSign(), dataToSign.getDigestAlgorithm());
    Signature signature = dataToSign.finalize(signatureValue);
    Assert.assertNotNull(signature);
  }

  @Test
  public void signAsiceContainerWithExtRsaTm() throws Exception {
    Container container = this.createNonEmptyContainer();
    DataToSign dataToSign = SignatureBuilder.aSignature(container).withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT_TM).withSigningCertificate(pkcs12SignatureToken.getCertificate()).
        buildDataToSign();
    Assert.assertNotNull(dataToSign);
    // This call mocks the using of external signing functionality with hashcode
    byte[] signatureValue = pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign());
    Signature signature = dataToSign.finalize(signatureValue);
    Assert.assertNotNull(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
    container.saveAsFile(this.getFileBy("bdoc"));
  }

  @Test
  public void signAsiceContainerWithExtRsaLt() throws Exception {
    Container container = this.createNonEmptyContainer();
    DataToSign dataToSign = SignatureBuilder.aSignature(container).withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT).withSigningCertificate(pkcs12SignatureToken.getCertificate()).
        buildDataToSign();
    Assert.assertNotNull(dataToSign);
    // This call mocks the using of external signing functionality with hashcode
    byte[] signatureValue = pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign());
    Signature signature = dataToSign.finalize(signatureValue);
    Assert.assertNotNull(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
    container.saveAsFile(this.getFileBy("asice"));
  }

  @Test
  public void signAsiceContainerWithExtEccTm() throws Exception {
    Security.addProvider(new BouncyCastleProvider());
    String TEST_ECC_PKI_CONTAINER = "src/test/resources/testFiles/p12/MadDogOY.p12";
    String TEST_ECC_PKI_CONTAINER_PASSWORD = "test";
    PKCS12SignatureToken token = new PKCS12SignatureToken(TEST_ECC_PKI_CONTAINER, TEST_ECC_PKI_CONTAINER_PASSWORD,
        "test of esteid-sk 2011: mad dog oy");
    Assert.assertEquals("test of esteid-sk 2011: mad dog oy", token.getAlias());
    Container container = this.createNonEmptyContainer();
    DataToSign dataToSign = SignatureBuilder.aSignature(container).withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT_TM).withSigningCertificate(token.getCertificate()).
        buildDataToSign();
    Assert.assertNotNull(dataToSign);
    // This call mocks the using of external signing functionality with hashcode
    byte[] signatureValue = new byte[1];
    int counter = 5;
    do {
      signatureValue = token.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign());
      counter--;
    } while (signatureValue.length == 72 && counter > 0); // Somehow the signature with length 72 is not correct
    Signature signature = dataToSign.finalize(signatureValue);
    Assert.assertNotNull(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
    container.saveAsFile(this.getFileBy("asice"));
  }

  @Test
  public void signAsiceContainerWithExtEccLt() throws Exception {
    Security.addProvider(new BouncyCastleProvider());
    String TEST_ECC_PKI_CONTAINER = "src/test/resources/testFiles/p12/MadDogOY.p12";
    String TEST_ECC_PKI_CONTAINER_PASSWORD = "test";
    PKCS12SignatureToken token = new PKCS12SignatureToken(TEST_ECC_PKI_CONTAINER, TEST_ECC_PKI_CONTAINER_PASSWORD,
        "test of esteid-sk 2011: mad dog oy");
    Assert.assertEquals("test of esteid-sk 2011: mad dog oy", token.getAlias());
    Container container = this.createNonEmptyContainer();
    DataToSign dataToSign = SignatureBuilder.aSignature(container).withSignatureDigestAlgorithm(DigestAlgorithm.SHA256).
        withSignatureProfile(SignatureProfile.LT).withSigningCertificate(token.getCertificate()).
        withEncryptionAlgorithm(EncryptionAlgorithm.ECDSA).buildDataToSign();
    Assert.assertNotNull(dataToSign);
    // This call mocks the using of external signing functionality with hashcode
    byte[] signatureValue = new byte[1];
    int counter = 5;
    do {
      signatureValue = token.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign());
      counter--;
    } while (signatureValue.length == 72 && counter > 0); // Somehow the signature with length 72 is not correct

    Signature signature = dataToSign.finalize(signatureValue);
    Assert.assertNotNull(signature);
    Assert.assertTrue(signature.validateSignature().isValid());
    container.addSignature(signature);
    Assert.assertTrue(container.validate().isValid());
    container.saveAsFile(this.getFileBy("asice"));
  }

  @Test
  public void invokeSigningForCustomContainer() throws Exception {
    ContainerBuilder.setContainerImplementation("TEST-FORMAT", CustomContainer.class);
    SignatureBuilder.setSignatureBuilderForContainerType("TEST-FORMAT", MockSignatureBuilder.class);
    Container container = TestDataBuilderUtil.createContainerWithFile(this.testFolder, "TEST-FORMAT");
    Signature signature = SignatureBuilder.aSignature(container).withSignatureToken(pkcs12SignatureToken).
        invokeSigning();
    Assert.assertNotNull(signature);
  }

  @Test
  public void invokingSigningBBesSignatureForAsicEContainer() {
    Container container = buildContainer(ASICE, ASICE_WITH_TS_SIG);
    assertAsicEContainer(container);

    Signature signature = SignatureBuilder.aSignature(container)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
            .withSignatureProfile(SignatureProfile.B_BES)
            .withSignatureToken(pkcs12SignatureToken)
            .invokeSigning();
    assertBBesSignature(signature);
  }

  @Test(expected = IllegalSignatureProfileException.class)
  public void invokingSigningTMSignatureForAsicEContainer_throwsException() {
    Container container = buildContainer(ASICE, ASICE_WITH_TS_SIG);
    assertAsicEContainer(container);

    SignatureBuilder.aSignature(container)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
            .withSignatureProfile(SignatureProfile.LT_TM)
            .withSignatureToken(pkcs12SignatureToken)
            .invokeSigning();
  }

  @Test(expected = IllegalSignatureProfileException.class)
  public void invokingSigningBEpesSignatureForAsicEContainer_throwsException() {
    Container container = buildContainer(ASICE, ASICE_WITH_TS_SIG);
    assertAsicEContainer(container);

    SignatureBuilder.aSignature(container)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
            .withSignatureProfile(SignatureProfile.B_EPES)
            .withSignatureToken(pkcs12SignatureToken)
            .invokeSigning();
  }

  @Test
  public void invokeSigning_whenOverridingBDocContainerFormat() {
    CustomContainer.type = "BDOC";
    ContainerBuilder.setContainerImplementation("BDOC", CustomContainer.class);
    SignatureBuilder.setSignatureBuilderForContainerType("BDOC", MockSignatureBuilder.class);
    Container container = this.createNonEmptyContainer();
    Signature signature = this.createSignatureBy(container, pkcs12SignatureToken);
    Assert.assertNotNull(signature);
    CustomContainer.resetType();
  }

  @Test
  public void buildingBEpesSignatureResultsWithBDocSignature() {
    Container container = buildContainer(BDOC, ASIC_WITH_NO_SIG);
    DataToSign dataToSign = SignatureBuilder.aSignature(container)
              .withSigningCertificate(pkcs12SignatureToken.getCertificate())
              .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
              .withSignatureProfile(SignatureProfile.B_EPES)
              .buildDataToSign();

    Signature signature = dataToSign.finalize(pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
    assertBEpesSignature(signature);
  }

  @Test
  public void bDocContainerWithTMSignature_signWithTimemarkSignature_shouldSucceed() {
    Container container = buildContainer(BDOC_WITH_TM_SIG);
    assertBDocContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT_TM);
    assertTimemarkSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimemarkSignature(container.getSignatures().get(1));
  }

  @Test
  public void bDocContainerWithTMSignature_signWithTimestampSignature_shouldSucceed() {
    Container container = buildContainer(BDOC_WITH_TM_SIG);
    assertBDocContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT);
    assertTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));
  }

  @Test
  public void bDocContainerWithTMSignature_signWithBEpesSignature_shouldSucceed() {
    Container container = buildContainer(BDOC_WITH_TM_SIG);
    assertBDocContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));

    Signature signature = signContainerWithSignature(container, SignatureProfile.B_EPES);
    assertBEpesSignature(signature);

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertBEpesSignature(container.getSignatures().get(1));
  }

  @Test
  public void bDocContainerWithTMAndTSSignature_signWithTimestampSignature_shouldSucceed() {
    Container container = buildContainer(BDOC_WITH_TM_AND_TS_SIG);
    assertBDocContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT);
    assertTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(3, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));
    assertTimestampSignature(container.getSignatures().get(2));
  }

  @Test
  public void bDocContainerWithTMAndTSSignature_signWithTimemarkSignature_shouldSucceed() {
    Container container = buildContainer(BDOC_WITH_TM_AND_TS_SIG);
    assertBDocContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT_TM);
    assertTimemarkSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(3, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));
    assertTimemarkSignature(container.getSignatures().get(2));
  }

  @Test
  public void bDocContainerWithTMAndTSSignature_signWithArchiveTimestampSignature_shouldSucceed() {
    Container container = buildContainer(BDOC_WITH_TM_AND_TS_SIG);
    assertBDocContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LTA);
    assertArchiveTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(3, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));
    assertArchiveTimestampSignature(container.getSignatures().get(2));
  }

  @Test
  public void bDocContainerWithoutSignatures_signWithoutAssignedProfile_defaultPofileIsUsed_shouldSucceedWithTimestampSignature() {
    Container container = buildContainer(BDOC, ASIC_WITH_NO_SIG);
    assertBDocContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
          .buildDataToSign();

    Signature signature = dataToSign.finalize(pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
    Assert.assertSame(Constant.Default.SIGNATURE_PROFILE, signature.getProfile());
    assertTimestampSignature(signature);
    assertValidSignature(signature);

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));
  }

  @Test
  public void bDocContainerWithoutSignatures_signWithTimestampSignature_shouldSucceed() {
    Container container = buildContainer(BDOC, ASIC_WITH_NO_SIG);
    assertBDocContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT);
    assertTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));
  }

  @Test
  public void bDocContainerWithoutSignatures_signWithTimemarkSignature_shouldSucceed() {
    Container container = buildContainer(BDOC, ASIC_WITH_NO_SIG);
    assertBDocContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT_TM);
    assertTimemarkSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertBDocContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimemarkSignature(container.getSignatures().get(0));
  }

  @Test
  public void asiceContainerWithoutSignatures_signWithoutAssignedProfile_defaultPofileIsUsed_shouldSucceedWithTimestampSignature() {
    Container container = buildContainer(ASICE, ASIC_WITH_NO_SIG);
    assertAsicEContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
          .buildDataToSign();

    Signature signature = dataToSign.finalize(pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
    Assert.assertSame(Constant.Default.SIGNATURE_PROFILE, signature.getProfile());
    assertTimestampSignature(signature);
    assertValidSignature(signature);

    container.addSignature(signature);
    assertAsicEContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));
  }

  @Test
  public void signWith256EcKey_withoutAssigningSignatureDigestAlgo_sha256SignatureDigestAlgoIsUsed() {
    Container container = buildContainer(ASICE, ASIC_WITH_NO_SIG);
    assertAsicEContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
            .withSigningCertificate(pkcs12EccSignatureToken.getCertificate())
            .buildDataToSign();

    Signature signature = dataToSign.finalize(pkcs12EccSignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
    Assert.assertEquals(DigestAlgorithm.SHA256, dataToSign.getSignatureParameters().getSignatureDigestAlgorithm());
    assertValidSignature(signature);
  }

  @Test
  public void signWith384EcKey_withoutAssigningSignatureDigestAlgo_sha384SignatureDigestAlgoIsUsed() {
    Container container = buildContainer(ASICE, ASIC_WITH_NO_SIG);
    assertAsicEContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
            .withSigningCertificate(pkcs12Esteid2018SignatureToken.getCertificate())
            .buildDataToSign();

    Signature signature = dataToSign.finalize(pkcs12Esteid2018SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
    Assert.assertEquals(DigestAlgorithm.SHA384, dataToSign.getSignatureParameters().getSignatureDigestAlgorithm());
    assertValidSignature(signature);
  }

  @Test
  public void signWithDifferentDataFileAndSignatureDigestAlgorithm() throws Exception {
    Container container = this.createNonEmptyContainer();
    DataToSign dataToSign = SignatureBuilder.aSignature(container)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA384)
            .withDataFileDigestAlgorithm(DigestAlgorithm.SHA512)
            .withSigningCertificate(pkcs12SignatureToken.getCertificate())
            .buildDataToSign();
    SignatureParameters signatureParameters = dataToSign.getSignatureParameters();
    Assert.assertEquals(DigestAlgorithm.SHA384, signatureParameters.getSignatureDigestAlgorithm());
    Assert.assertEquals(DigestAlgorithm.SHA512, signatureParameters.getDataFileDigestAlgorithm());
    Signature signature = dataToSign.finalize(pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
    Assert.assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha384", signature.getSignatureMethod());
    Assert.assertTrue(container.validate().isValid());
  }

  @Test
  public void asiceContainerWithoutSignatures_signWithTimestampSignature_shouldSucceed() {
    Container container = buildContainer(ASICE, ASIC_WITH_NO_SIG);
    assertAsicEContainer(container);
    Assert.assertTrue(container.getSignatures().isEmpty());

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT);
    assertTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertAsicEContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));
  }

  @Test(expected = IllegalSignatureProfileException.class)
  public void asiceContainerWithoutSignatures_signWithTimemarkSignature_shouldFail() {
    Container container = buildContainer(ASICE, ASIC_WITH_NO_SIG);
    assertAsicEContainer(container);

    Assert.assertTrue(container.getSignatures().isEmpty());

    buildDataToSign(container, SignatureProfile.LT_TM);
  }

  @Test
  public void asicEContainerWithTSSignature_signWithTimestampSignature_shouldSucceed() {
    Container container = buildContainer(ASICE_WITH_TS_SIG);
    assertAsicEContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LT);
    assertTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertAsicEContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));
    assertTimestampSignature(container.getSignatures().get(1));
  }

  @Test
  public void asicEContainerWithTSSignature_signWithArchiveTimestampSignature_shouldSucceed() {
    Container container = buildContainer(ASICE_WITH_TS_SIG);
    assertAsicEContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));

    Signature signature = signContainerWithSignature(container, SignatureProfile.LTA);
    assertArchiveTimestampSignature(signature);
    Assert.assertTrue(signature.validateSignature().isValid());

    container.addSignature(signature);
    assertAsicEContainer(container);
    Assert.assertSame(2, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));
    assertArchiveTimestampSignature(container.getSignatures().get(1));
  }

  @Test(expected = IllegalSignatureProfileException.class)
  public void asicEContainerWithTSSignature_signWithTimemarkSignature_shouldFail() {
    Container container = buildContainer(ASICE_WITH_TS_SIG);
    assertAsicEContainer(container);
    Assert.assertSame(1, container.getSignatures().size());
    assertTimestampSignature(container.getSignatures().get(0));

    buildDataToSign(container, SignatureProfile.LT_TM);
  }

  @Test
  public void customSignaturePolicyAllowedForLT_TMSignatureProfile_resultsWithLTProfileBDocSignature() {
    Container container = ContainerBuilder.aContainer(BDOC).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes()), "name", "text/plain");
    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withSignatureProfile(SignatureProfile.LT_TM)
          .withOwnSignaturePolicy(validCustomPolicy())
          .buildDataToSign();

    byte[] signatureValue = pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign());
    Signature signature = dataToSign.finalize(signatureValue);

    Assert.assertNotNull(signature);
    Assert.assertTrue(signature instanceof BDocSignature);
    Assert.assertEquals(SignatureProfile.LT, signature.getProfile());
  }

  @Test
  public void customSignaturePolicyWhenSignatureProfileNotSet_resultsWithTimestampSignature() {
    Container container = ContainerBuilder.aContainer(BDOC).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes()), "name", "text/plain");
    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withOwnSignaturePolicy(validCustomPolicy())
          .buildDataToSign();

    byte[] signatureValue = pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign());
    Signature signature = dataToSign.finalize(signatureValue);
    assertTimestampSignature(signature);
  }

  @Test(expected = NotSupportedException.class)
  public void signatureProfileLTNotAllowedForCustomSignaturePolicy() {
    Container container = ContainerBuilder.aContainer(BDOC).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes()), "name", "text/plain");
    SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .withOwnSignaturePolicy(validCustomPolicy())
          .withSignatureProfile(SignatureProfile.LT)
          .buildDataToSign();
  }

  @Test(expected = NotSupportedException.class)
  public void customSignaturePolicyNotAllowedForLTSignatureProfile() {
    Container container = ContainerBuilder.aContainer(ASICE).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes()), "name", "text/plain");
    SignatureBuilder.aSignature(container)
          .withSignatureProfile(SignatureProfile.LT)
          .withOwnSignaturePolicy(validCustomPolicy())
          .buildDataToSign();
  }

  @Test
  public void claimedSigningTimeInitializedDuringDataToSignBuilding() {
    Container container = ContainerBuilder.aContainer(BDOC).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes()), "name", "text/plain");

    long claimedSigningTimeLowerBound = new Date().getTime() / 1000 * 1000;
    DataToSign dataToSign = buildDataToSign(container, SignatureProfile.LT_TM);
    long claimedSigningTimeUpperBound = new Date().getTime() + 1000;

    long claimedSigningTime = dataToSign.getSignatureParameters().getClaimedSigningDate().getTime();
    assertTrue(claimedSigningTime >= claimedSigningTimeLowerBound);
    assertTrue(claimedSigningTime <= claimedSigningTimeUpperBound);
  }

  @Test
  public void invokeSigning_networkExceptionIsNotCaught() {
    Configuration configuration = Configuration.of(TEST);
    configuration.setOcspSource("http://invalid.ocsp.url");

    expectedException.expect(ServiceUnreachableException.class);
    expectedException.expectMessage("Failed to connect to OCSP service <" + configuration.getOcspSource() + ">");

    Container container = ContainerBuilder.aContainer(Container.DocumentType.BDOC).withConfiguration(configuration).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes(StandardCharsets.UTF_8)), "file name", "text/plain");

    SignatureBuilder.aSignature(container)
          .withSignatureToken(pkcs12SignatureToken)
          .invokeSigning();
  }

  @Test
  public void dataToSignFinalize_networkExceptionIsNotCaught() {
    Configuration configuration = Configuration.of(TEST);
    configuration.setOcspSource("http://invalid.ocsp.url");

    expectedException.expect(ServiceUnreachableException.class);
    expectedException.expectMessage("Failed to connect to OCSP service <" + configuration.getOcspSource() + ">");

    Container container = ContainerBuilder.aContainer(Container.DocumentType.BDOC).withConfiguration(configuration).build();
    container.addDataFile(new ByteArrayInputStream("something".getBytes(StandardCharsets.UTF_8)), "file name", "text/plain");

    DataToSign dataToSign = SignatureBuilder.aSignature(container)
          .withSigningCertificate(pkcs12SignatureToken.getCertificate())
          .buildDataToSign();
    dataToSign.finalize(pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
  }

  private Signature signContainerWithSignature(Container container, SignatureProfile signatureProfile) {
    DataToSign dataToSign = buildDataToSign(container, signatureProfile);
    Assert.assertNotNull(dataToSign);
    Assert.assertEquals(signatureProfile, dataToSign.getSignatureParameters().getSignatureProfile());

    return dataToSign.finalize(pkcs12SignatureToken.sign(dataToSign.getDigestAlgorithm(), dataToSign.getDataToSign()));
  }

  private DataToSign buildDataToSign(Container container, SignatureProfile signatureProfile) {
    return SignatureBuilder.aSignature(container)
              .withSigningCertificate(pkcs12SignatureToken.getCertificate())
              .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
              .withSignatureProfile(signatureProfile)
              .buildDataToSign();
  }

  private Container buildContainer(Container.DocumentType documentType, String path) {
    try (InputStream stream = FileUtils.openInputStream(new File(path))) {
      return BDocContainerBuilder
              .aContainer(documentType)
              .fromStream(stream)
              .build();
    } catch (IOException e) {
      fail("Failed to read container from stream");
      throw new IllegalStateException(e);
    }
  }

  private Container buildContainer(String path) {
    try (InputStream stream = FileUtils.openInputStream(new File(path))) {
      return BDocContainerBuilder
              .aContainer(Container.DocumentType.BDOC)
              .fromStream(stream)
              .build();
    } catch (IOException e) {
      fail("Failed to read container from stream");
      throw new IllegalStateException(e);
    }
  }

  /*
   * RESTRICTED METHODS
   */

  @Override
  protected void after() {
    ContainerBuilder.removeCustomContainerImplementations();
    SignatureBuilder.removeCustomSignatureBuilders();
  }

  private Signature createBDocSignatureWithProfile(SignatureProfile signatureProfile) {
    Container container = this.createNonEmptyContainer();
    Signature signature = this.createSignatureBy(container, signatureProfile, pkcs12SignatureToken);
    return signature;
  }

  private Signature openSignatureFromExistingSignatureDocument(Container container) throws IOException {
    Signature signature = this.openAdESSignature(container);
    Assert.assertEquals("id-6a5d6671af7a9e0ab9a5e4d49d69800d", signature.getId());
    return signature;
  }

  private Signature openAdESSignature(Container container) throws IOException {
    byte[] signatureBytes = FileUtils.readFileToByteArray(new File("src/test/resources/testFiles/xades/valid-bdoc-tm.xml"));
    return SignatureBuilder.aSignature(container).openAdESSignature(signatureBytes);
  }

  private void assertSignatureIsValid(Signature signature, SignatureProfile expectedSignatureProfile) {
    Assert.assertNotNull(signature.getOCSPResponseCreationTime());
    Assert.assertEquals(expectedSignatureProfile, signature.getProfile());
    Assert.assertNotNull(signature.getClaimedSigningTime());
    Assert.assertNotNull(signature.getAdESSignature());
    Assert.assertTrue(signature.getAdESSignature().length > 1);
    Assert.assertTrue(signature.validateSignature().isValid());
  }
}
