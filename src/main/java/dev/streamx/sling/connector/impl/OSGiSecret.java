package dev.streamx.sling.connector.impl;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OSGiSecret {

  private static final Logger LOG = LoggerFactory.getLogger(OSGiSecret.class);
  private final String value;

  OSGiSecret(String value) {
    // Interpolation fails when `secretsdir` property isn't set,
    // what is the case by default for local AEMaaCS:
    // https://github.com/apache/felix-dev/blob/e479f6517c4bcbf5fcdd73c20b8760fe1610ab3a/configadmin-plugins/interpolation/README.md?plain=1#L133
    boolean interpolationFailed = value.startsWith("$[secret:") && value.endsWith("]");
    if (interpolationFailed) {
      LOG.warn("Interpolation failed for secret: {}. Empty value will be used as a fallback", value);
      this.value = StringUtils.EMPTY;
    } else {
      this.value = value;
    }
  }

  String get() {
    return value;
  }
}
