package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.util.SimpleInternalRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class stores and updates hashes of last published contents for resources.
 */
public final class ResourceHashManager {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceHashManager.class);
  private static final String BASE_NODE_PATH_FOR_RESOURCE_HASHES = "/var/streamx/connector/sling/resources/hashes";
  private static final String PN_LAST_PUBLISH_HASH = "lastPublishHash";

  private ResourceHashManager() {
    // no instances
  }

  /**
   * Loads current hash for the Resource from JCR, and overwrites the hash if it has changed for given {@code resourcePath}
   * @return true if the hash has changed, false otherwise
   */
  static boolean hasResourceContentChanged(String resourcePath, SlingRequestProcessor slingRequestProcessor, ResourceResolver resourceResolver) {
    try {
      Session session = Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
      Node hashNode = getOrCreateHashNode(resourcePath, session);

      byte[] resourceContent = readResourceContent(resourcePath, slingRequestProcessor, resourceResolver);
      String newHash = computeHash(resourceContent);
      if (hashNode.hasProperty(PN_LAST_PUBLISH_HASH)) {
        String oldHash = hashNode.getProperty(PN_LAST_PUBLISH_HASH).getString();
        if (newHash.equals(oldHash)) {
          return false;
        }
      }

      hashNode.setProperty(PN_LAST_PUBLISH_HASH, newHash);
      session.save();
      return true;
    } catch (Exception ex) {
      LOG.error("Error verifying if hash for resource '{}' has changed", resourcePath, ex);
      return true;
    }
  }

  static void deleteResourceHash(String resourcePath, ResourceResolver resourceResolver) {
    try {
      Session session = Objects.requireNonNull(resourceResolver.adaptTo(Session.class));

      String hashNodePath = getJcrPathForResource(resourcePath);
      Resource hashResource = resourceResolver.getResource(hashNodePath);
      if (hashResource != null) {
        resourceResolver.delete(hashResource);
        session.save();
      }
    } catch (Exception e) {
      LOG.error("Error deleting hash for resource '{}'", resourcePath, e);
    }
  }

  private static byte[] readResourceContent(String resourcePath, SlingRequestProcessor slingRequestProcessor, ResourceResolver resourceResolver) {
    SlingUri slingUri = SlingUriBuilder.parse(resourcePath, resourceResolver).build();
    SimpleInternalRequest internalRequest = new SimpleInternalRequest(slingUri, slingRequestProcessor, resourceResolver);
    return internalRequest.getResponseAsBytes().orElse(new byte[0]);
  }

  private static String getJcrPathForResource(String resourcePath) {
    return BASE_NODE_PATH_FOR_RESOURCE_HASHES + resourcePath;
  }

  private static Node getOrCreateHashNode(String resourcePath, Session session) throws RepositoryException {
    String hashNodePath = getJcrPathForResource(resourcePath);
    return JcrUtils.getOrCreateByPath(hashNodePath, "sling:Folder", "nt:unstructured", session, true);
  }

  private static String computeHash(byte[] bytes) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hashBytes = digest.digest(bytes);
    return bytesToHex(hashBytes);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

}
