package org.pitest.mutationtest.report.github.reporter;

import java.util.Properties;

import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;
import org.pitest.mutationtest.report.github.GitTcParams;

public class GitHubPullRequestFactory implements MutationResultListenerFactory {

  @Override
  public MutationResultListener getListener(Properties props, ListenerArguments args) {
    GitTcParams gitTcParams = GitTcParams.builder()
        .githubUrl(args.data().getGithubUrl())
        .githubToken(args.data().getGithubToken())
        .githubRepo(args.data().getGithubRepo())
        .githubPrNumber(args.data().getPrNumber())
        .build();

    return new GithubPullRequestReporter(gitTcParams);
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
