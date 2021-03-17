package org.pitest.mutationtest.report.github;

import java.util.Properties;

import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;

public class GitHubReportFactory implements MutationResultListenerFactory {

  @Override
  public MutationResultListener getListener(Properties props, ListenerArguments args) {
    GitTcParams gitTcParams = GitTcParams.builder()
        .buildId(args.data().getBuildId())
        .buildTypeId(args.data().getBuildTypeId())
        .currentDirPath(args.data().getCurrentDirectory())
        .hostname(args.data().getHostname())
        .lastProjectInReactor(args.data().isLastProjectInReactor())
        .prArtifactsPath(args.data().getPrArtifactsPath())
        .build();

    return new GitHubSingleReportListener(args.getCoverage(), args.getEngine().getMutatorNames(),
        gitTcParams, args.getLocator());
  }

  @Override
  public String name() {
    return "GITHUB";
  }

  @Override
  public String description() {
    return "GitHub Report";
  }
}
