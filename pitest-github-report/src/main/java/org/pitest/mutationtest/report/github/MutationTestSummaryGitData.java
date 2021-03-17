package org.pitest.mutationtest.report.github;

import java.util.Collection;

import org.pitest.classinfo.ClassInfo;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.report.html.MutationTestSummaryData;

import static org.pitest.mutationtest.report.github.ResultIcon.CHECK_FILE_FAIL;
import static org.pitest.mutationtest.report.github.ResultIcon.CHECK_FILE_PASS;

public class MutationTestSummaryGitData extends MutationTestSummaryData {

  private static final int THRESHOLD = 50;

  private final GitTcParams params;

  public MutationTestSummaryGitData(String fileName, Collection<MutationResult> results,
      Collection<String> mutators, Collection<ClassInfo> classes,
      long numberOfCoveredLines, GitTcParams params) {
    super(fileName, results, mutators, classes, numberOfCoveredLines);
    this.params = params;
  }

  public boolean isPass() {
    return getTotals().getMutationCoverage() >= THRESHOLD;
  }

  public String getIcon() {
    return isPass()? CHECK_FILE_PASS : CHECK_FILE_FAIL;
  }

  public String getUrl() {
    return "http://" + params.getHostname() + "/repository/download/" + params.getBuildTypeId()
        + "/" + params.getBuildId() + ":id/"+ params.getPrArtifactsPath() + "/" + params.getCurrentDirPath() + "/target/pit-reports/"
        + getPackageName() + "/" + getFileName() + ".html";
  }

  public String getLineCoverageBarUrl() {
    return "![](https://progress-bar.dev/"+getTotals().getNumberOfLinesCovered()
        +"/?scale=" +getTotals().getNumberOfLines()
        +"&suffix=/"+getTotals().getNumberOfLines()+")";
  }

  public String getMutationCoverageBarUrl() {
    return "![](https://progress-bar.dev/"+getTotals().getNumberOfMutationsDetected()
        +"/?scale=" +getTotals().getNumberOfMutations()
        +"&suffix=/"+getTotals().getNumberOfMutations()+")";
  }

  public String getTestStrengthBarUrl() {
    return "![](https://progress-bar.dev/"+getTotals().getNumberOfMutationsDetected()
        +"/?scale=" +getTotals().getNumberOfMutationsWithCoverage()
        +"&suffix=/"+getTotals().getNumberOfMutationsWithCoverage()+")";
  }
}
