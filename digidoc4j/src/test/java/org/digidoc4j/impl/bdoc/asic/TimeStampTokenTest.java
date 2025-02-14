package org.digidoc4j.impl.bdoc.asic;

import java.io.FileInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.digidoc4j.AbstractTest;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.ContainerOpener;
import org.digidoc4j.SignatureValidationResult;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.impl.asic.TimeStampContainerValidationResult;
import org.digidoc4j.impl.asic.manifest.ManifestValidator;
import org.digidoc4j.test.TestAssert;
import org.digidoc4j.test.util.TestDigiDoc4JUtil;
import org.digidoc4j.test.util.TestSigningUtil;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.MimeType;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.timestamp.TimestampToken;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.TimestampType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by Andrei on 22.11.2017.
 */

public class TimeStampTokenTest extends AbstractTest {

  public static final String META_INF_TIMESTAMP_TST = "META-INF/timestamp.tst";

  @Rule
  public final SystemOutRule stdOut = new SystemOutRule().enableLog();

  @Test
  public void testCreateTimeStampContainer() {
    Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS).withConfiguration(this.configuration).
        withDataFile("src/test/resources/testFiles/helper-files/test.txt", "text/plain").
        withTimeStampToken(DigestAlgorithm.SHA256).build();
    container.saveAsFile(this.getFileBy("asics"));
    TestAssert.assertContainerIsValid(container);
    assertNotNull(container.getTimeStampToken());
  }

  @Test
  public void testOpenTimeStampContainer() {
    Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS).withConfiguration(this.configuration).
        fromExistingFile("src/test/resources/testFiles/valid-containers/testtimestamp.asics").build();
    TestAssert.assertContainerIsValid(container);
    assertNotNull(container.getTimeStampToken());
    assertEquals(2001, container.getTimeStampToken().getBytes().length);
  }

  @Test
  public void testOpenValidTimeStampContainer() {
    Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS).withConfiguration(this.configuration).
        fromExistingFile("src/test/resources/testFiles/valid-containers/timestamptoken-ddoc.asics").build();
    TimeStampContainerValidationResult validate = (TimeStampContainerValidationResult) container.validate();
    Assert.assertEquals("SK TIMESTAMPING AUTHORITY", validate.getSignedBy());
    Assert.assertEquals(Indication.TOTAL_PASSED, validate.getIndication());
    Assert.assertTrue(validate.isValid());
  }

  @Test(expected = DigiDoc4JException.class)
  public void testOpenContainerTwoDataFiles() {
    Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS).withConfiguration(this.configuration).
        fromExistingFile("src/test/resources/testFiles/invalid-containers/timestamptoken-two-data-files.asics").build();
    container.validate();
  }

  @Test(expected = DigiDoc4JException.class)
  public void testOpenInvalidTimeStampContainer() {
    Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS).withConfiguration(this.configuration).
        fromExistingFile("src/test/resources/testFiles/invalid-containers/timestamptoken-invalid.asics").build();
    container.validate();
  }

  @Test
  public void generatedTimestampToken() throws Exception {
    try (FileInputStream fis = new FileInputStream("src/test/resources/testFiles/tst/timestamp.tst")) {
      TimestampToken token = new TimestampToken(Utils.toByteArray(fis), TimestampType.ARCHIVE_TIMESTAMP);
      assertNotNull(token);
      assertNotNull(token.getGenerationTime());
      Assert.assertTrue(Utils.isCollectionNotEmpty(token.getCertificates()));
      assertNull(token.getSignatureAlgorithm());
      Assert.assertTrue(token.isSignedBy(token.getCertificates().get(0)));
      assertNotNull(token.getSignatureAlgorithm());
      Assert.assertEquals(TimestampType.ARCHIVE_TIMESTAMP, token.getTimeStampType());
      Assert.assertEquals(DigestAlgorithm.SHA256, token.getMessageImprint().getAlgorithm());
      Assert.assertEquals(SignatureAlgorithm.RSA_SHA512, token.getSignatureAlgorithm());
      Assert.assertTrue(Utils.isStringNotBlank(Utils.toBase64(token.getMessageImprint().getValue())));
      Assert.assertFalse(token.isSelfSigned());
      Assert.assertFalse(token.matchData(new byte[]{1, 2, 3}));
      Assert.assertTrue(token.isMessageImprintDataFound());
      Assert.assertFalse(token.isMessageImprintDataIntact());
      Assert.assertTrue(token.isMessageImprintDataFound());
    }
  }

  @Test
  public void createsContainerWithTstASICS() throws Exception {
    String fileName = this.getFileBy("asics");
    String[] parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/test.txt",
        "text/plain", "-datst", "SHA256", "-tst"};
    TestDigiDoc4JUtil.call(parameters);
    ZipFile zipFile = new ZipFile(fileName);
    ZipEntry mimeTypeEntry = zipFile.getEntry(ManifestValidator.MIMETYPE_PATH);
    ZipEntry manifestEntry = zipFile.getEntry(ManifestValidator.MANIFEST_PATH);
    ZipEntry timestampEntry = zipFile.getEntry(META_INF_TIMESTAMP_TST);
    assertNotNull(mimeTypeEntry);
    assertNotNull(manifestEntry);
    assertNotNull(timestampEntry);
    String mimeTypeContent = this.getFileContent(zipFile.getInputStream(mimeTypeEntry));
    Assert.assertTrue(mimeTypeContent.contains(MimeType.ASICS.getMimeTypeString()));
    String manifestContent = this.getFileContent(zipFile.getInputStream(manifestEntry));
    Assert.assertTrue(manifestContent.contains(MimeType.ASICS.getMimeTypeString()));
    Container container = ContainerOpener.open(fileName);
    SignatureValidationResult validate = container.validate();
    Assert.assertTrue(validate.isValid());
    Assert.assertEquals("ASICS", container.getType());
  }

  @Test
  public void tstASICSAddTwoSignatures() throws Exception {
    String fileName = this.getFileBy("asics");
    String[] parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/test.txt",
        "text/plain", "-datst", "SHA256", "-tst"};
    TestDigiDoc4JUtil.call(parameters);
    parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/dds_колючей стерне.txt",
        "text/plain", "-datst", "SHA256", "-tst"};
    TestDigiDoc4JUtil.call(parameters);
    Assert.assertThat(this.stdOut.getLog(), StringContains.containsString(
        "This container has already timestamp. Should be no signatures in case of timestamped ASiCS container."));
  }

  @Test
  public void tstASICSAddTwoFiles() throws Exception {
    String fileName = this.getFileBy("asics");
    String[] parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/test.txt",
        "text/plain", "-datst", "SHA256", "-tst"};
    TestDigiDoc4JUtil.call(parameters);
    parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/dds_колючей стерне.txt",
        "text/plain"};
    TestDigiDoc4JUtil.call(parameters);
    Assert.assertThat(this.stdOut.getLog(), StringContains.containsString(
        "This container has already timestamp. Should be no signatures in case of timestamped ASiCS container."));
  }

  @Test
  public void tstASICSAddPKCS12Signature() throws Exception {
    String fileName = this.getFileBy("asics");
    String[] parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/test.txt",
        "text/plain", "-datst", "SHA256", "-tst"};
    TestDigiDoc4JUtil.call(parameters);
    parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/dds_колючей стерне.txt",
        "text/plain", "-pkcs12", TestSigningUtil.TEST_PKI_CONTAINER, TestSigningUtil.TEST_PKI_CONTAINER_PASSWORD};
    TestDigiDoc4JUtil.call(parameters);
    Assert.assertThat(this.stdOut.getLog(), StringContains.containsString(
        "This container has already timestamp. Should be no signatures in case of timestamped ASiCS container."));
  }

  @Test
  public void asicsAddPKCS12Signature() throws Exception {
    String fileName = this.getFileBy("asics");
    String[] parameters = new String[]{"-in", fileName, "-type", "ASICS", "-add", "src/test/resources/testFiles/helper-files/dds_колючей стерне.txt",
        "text/plain", "-pkcs12", TestSigningUtil.TEST_PKI_CONTAINER, TestSigningUtil.TEST_PKI_CONTAINER_PASSWORD};
    TestDigiDoc4JUtil.call(parameters);
    Assert.assertThat(this.stdOut.getLog(), StringContains.containsString("Not supported: Not for ASiC-S container"));

  }
  
  /*
   * RESTRICTED METHODS
   */

  @Override
  protected void before() {
    this.configuration = new Configuration(Configuration.Mode.TEST);
  }

}