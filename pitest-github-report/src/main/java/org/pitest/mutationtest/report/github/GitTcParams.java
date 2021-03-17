package org.pitest.mutationtest.report.github;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class GitTcParams {

  private String hostname;
  private String buildTypeId;
  private String buildId;
  private String currentDirPath;
  private boolean lastProjectInReactor;
  private String githubUrl;
  private String githubToken;
  private String githubRepo;
  private int githubPrNumber;
  private String prArtifactsPath;

}
