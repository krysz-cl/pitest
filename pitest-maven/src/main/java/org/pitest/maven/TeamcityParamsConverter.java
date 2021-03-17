package org.pitest.maven;

import org.apache.maven.project.MavenProject;

public class TeamcityParamsConverter {

  String computeCurrentDirPath(AbstractPitMojo mojo) {
    MavenProject root = mojo.getProject();

    StringBuilder currentDir = new StringBuilder(mojo.getProject().getArtifactId());
    while (root.getParent() != null && !root.getArtifactId().equals(mojo.getProjectRootName())) {
      root = root.getParent();
      currentDir.insert(0, root.getArtifactId() + "/");
    }
    return currentDir.toString().replaceFirst(root.getArtifactId() + "/", "");
  }

}
