package org.pitest.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.pitest.coverage.CoverageSummary;
import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.statistics.MutationStatistics;
import org.pitest.mutationtest.tooling.CombinedStatistics;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.plugin.ClientClasspathPlugin;
import org.pitest.plugin.ToolClasspathPlugin;
import org.pitest.util.StringUtil;
import org.slf4j.bridge.SLF4JBridgeHandler;

import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

public class AbstractPitMojo extends AbstractMojo {

  private final Predicate<MavenProject> notEmptyProject;
  
  private final Predicate<Artifact>   filter;

  private final PluginServices        plugins;

  // Concrete List types declared for all fields to work around maven 2 bug

  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;
  /**
   * Test plugin to use
   */
  @Parameter(property = "testPlugin", defaultValue = "")
  private String testPlugin;

  /**
   * Classes to include in mutation test
   */
  @Parameter(property = "targetClasses")
  private ArrayList<String>           targetClasses;

  @Parameter(property = "targetClassesCommaSep")
  private String                      targetClassesCommaSep;

  /**
   * Tests to run
   */
  @Parameter(property = "targetTests")
  private ArrayList<String>           targetTests;

  @Parameter(property = "targetTestsCommaSep")
  private String                      targetTestsCommaSep;

  /**
   * Methods not to mutate
   */
  @Parameter(property = "excludedMethods")
  private ArrayList<String>           excludedMethods;

  /**
   * Classes not to mutate
   */
  @Parameter(property = "excludedClasses")
  private ArrayList<String>           excludedClasses;
  
  /**
   * Classes not to run tests from
   */
  @Parameter(property = "excludedTestClasses")
  private ArrayList<String>           excludedTestClasses;


  /**
   * Globs to be matched against method calls. No mutations will be created on
   * the same line as a match.
   */
  @Parameter(property = "avoidCallsTo")
  private ArrayList<String>           avoidCallsTo;

  /**
   * Base directory where all reports are written to.
   */
  @Parameter(defaultValue = "${project.build.directory}/pit-reports", property = "reportsDirectory")
  private File                        reportsDirectory;

  /**
   * File to write history information to for incremental analysis
   */
  @Parameter(property = "historyOutputFile")
  private File                        historyOutputFile;

  /**
   * File to read history from for incremental analysis (can be same as output
   * file)
   */
  @Parameter(property = "historyInputFile")
  private File                        historyInputFile;
  
  /**
   * Convenience flag to read and write history to a local temp file.
   * 
   * Setting this flag is the equivalent to calling maven with -DhistoryInputFile=file -DhistoryOutputFile=file
   * 
   * Where file is a file named [groupid][artifactid][version]_pitest_history.bin in the temp directory
   * 
   */
  @Parameter(defaultValue = "false", property = "withHistory")
  private boolean                     withHistory;

  /**
   * Teamcity buildId
   */
  @Parameter(property = "buildId")
  private String                     buildId;

  /**
   * Teamcity buildTypeId
   */
  @Parameter(property = "buildTypeId")
  private String                     buildTypeId;

  /**
   * Teamcity hostname
   */
  @Parameter(property = "hostname")
  private String                     hostname;

  /**
   * Maximum distance to look from test to class. Relevant when mutating static
   * initializers
   *
   */
  @Parameter(defaultValue = "-1", property = "maxDependencyDistance")
  private int                         maxDependencyDistance;

  /**
   * Number of threads to use
   */
  @Parameter(defaultValue = "1", property = "threads")
  private int                         threads;

  /**
   * Mutate static initializers
   */
  @Parameter(defaultValue = "false", property = "mutateStaticInitializers")
  private boolean                     mutateStaticInitializers;

  /**
   * Detect inlined code
   */
  @Parameter(defaultValue = "true", property = "detectInlinedCode")
  private boolean                     detectInlinedCode;

  /**
   * Mutation operators to apply
   */
  @Parameter(property = "mutators")
  private ArrayList<String>           mutators;
  
  /**
   * Mutation operators to apply
   */
  @Parameter(property = "features")
  private ArrayList<String>           features;


  /**
   * Weighting to allow for timeouts
   */
  @Parameter(defaultValue = "1.25", property = "timeoutFactor")
  private float                       timeoutFactor;

  /**
   * Constant factor to allow for timeouts 
   */
  @Parameter(defaultValue = "3000", property = "timeoutConstant")
  private long                        timeoutConstant;

  /**
   * Maximum number of mutations to allow per class
   */
  @Parameter(defaultValue = "-1", property = "maxMutationsPerClass")
  private int                         maxMutationsPerClass;

  /**
   * Arguments to pass to child processes
   */
  @Parameter
  private ArrayList<String>           jvmArgs;

  /**
   * Formats to output during analysis phase
   */
  @Parameter(property = "outputFormats")
  private ArrayList<String>           outputFormats;

  /**
   * Output verbose logging
   */
  @Parameter(defaultValue = "false", property = "verbose")
  private boolean                     verbose;

  /**
   * Throw error if no mutations found
   */
  @Parameter(defaultValue = "true", property = "failWhenNoMutations")
  private boolean                     failWhenNoMutations;

  /**
   * Create timestamped subdirectory for report
   */
  @Parameter(defaultValue = "true", property = "timestampedReports")
  private boolean                     timestampedReports;

  /**
   * TestNG Groups/JUnit Categories to exclude
   */
  @Parameter(property = "excludedGroups")
  private ArrayList<String>           excludedGroups;

  /**
   * TestNG Groups/JUnit Categories to include
   */
  @Parameter(property = "includedGroups")
  private ArrayList<String>           includedGroups;

  /**
   * Test methods that should be included for challenging the mutants
   */
  @Parameter(property = "includedTestMethods")
  private ArrayList<String>           includedTestMethods;

  /**
   * Whether to create a full mutation matrix.
   * 
   * If set to true all tests covering a mutation will be executed,
   * if set to false the test execution will stop after the first killing test.
   */
  @Parameter(property = "fullMutationMatrix", defaultValue = "false")

  private boolean                     fullMutationMatrix;
  /**
   * Maximum number of mutations to include in a single analysis unit.
   * 
   * If set to 1 will analyse very slowly, but with strong (jvm per mutant)
   * isolation.
   *
   */
  @Parameter(property = "mutationUnitSize")
  private int                         mutationUnitSize;

  /**
   * Export line coverage data
   */
  @Parameter(defaultValue = "false", property = "exportLineCoverage")
  private boolean                     exportLineCoverage;

  /**
   * Mutation score threshold at which to fail build
   */
  @Parameter(defaultValue = "0", property = "mutationThreshold")
  private int                         mutationThreshold;

  /**
   * Test strength score threshold at which to fail build
   */
  @Parameter(defaultValue = "0", property = "testStrengthThreshold")
  private int                         testStrengthThreshold;

  /**
   * Maximum surviving mutants to allow
   */
  @Parameter(defaultValue = "-1", property = "maxSurviving")
  private int                         maxSurviving = -1;
    
  /**
   * Line coverage threshold at which to fail build
   */
  @Parameter(defaultValue = "0", property = "coverageThreshold")
  private int                         coverageThreshold;

  /**
   * Path to java executable to use when running tests. Will default to
   * executable in JAVA_HOME if none set.
   */
  @Parameter
  private String                      jvm;

  /**
   * Engine to use when generating mutations.
   */
  @Parameter(defaultValue = "gregor", property = "mutationEngine")
  private String                      mutationEngine;

  /**
   * List of additional classpath entries to use when looking for tests and
   * mutable code. These will be used in addition to the classpath with which
   * PIT is launched.
   */
  @Parameter(property = "additionalClasspathElements")
  private ArrayList<String>           additionalClasspathElements;

  /**
   * List of classpath entries, formatted as "groupId:artifactId", which should
   * not be included in the classpath when running mutation tests. Modelled
   * after the corresponding Surefire/Failsafe property.
   */
  @Parameter(property = "classpathDependencyExcludes")
  private ArrayList<String>           classpathDependencyExcludes;
  
  /**
   * 
   */
  @Parameter(property = "excludedRunners")
  private ArrayList<String>           excludedRunners;

  /**
   * When set indicates that analysis of this project should be skipped
   */
  @Parameter(property = "skipPitest", defaultValue = "false")
  private boolean                     skip;

  /**
   * When set will try and create settings based on surefire configuration. This
   * may not give the desired result in some circumstances
   */
  @Parameter(defaultValue = "true")
  private boolean                     parseSurefireConfig;

  /**
   * honours common skipTests flag in a maven run
   */
  @Parameter(defaultValue = "false")
  private boolean                     skipTests;

  /**
   * When set will ignore failing tests when computing coverage. Otherwise, the
   * run will fail. If parseSurefireConfig is true, will be overridden from
   * surefire configuration property testFailureIgnore
   */
  @Parameter(defaultValue = "false")
  private boolean                     skipFailingTests;

  /**
   * Use slf4j for logging
   */
  @Parameter(defaultValue = "false", property = "useSlf4j")
  private boolean                     useSlf4j;

  /**
   * Configuration properties.
   *
   * Value pairs may be used by pitest plugins.
   *
   */
  @Parameter
  private Map<String, String>         pluginConfiguration;

  /**
   * environment configuration
   *
   * Value pairs may be used by pitest plugins.
   */
  @Parameter
  private Map<String, String>         environmentVariables = new HashMap<>();

  /**
   * <i>Internal</i>: Project to interact with.
   *
   */
  @Parameter(property = "project", readonly = true, required = true)
  private MavenProject                project;

  /**
   * <i>Internal</i>: Map of plugin artifacts.
   */
  @Parameter(property = "plugin.artifactMap", readonly = true, required = true)
  private Map<String, Artifact>       pluginArtifactMap;
  
  
  /**
   * Communicate the classpath using a temporary jar with a classpath
   * manifest. This allows support of very large classpaths but may cause
   * issues with certian libraries.
   */
  @Parameter(property = "useClasspathJar", defaultValue = "false")
  private boolean                     useClasspathJar;

  private final GoalStrategy          goalStrategy;

  @Parameter(property = "githubToken")
  private String githubToken;

  @Parameter(property = "githubRepo")
  private String githubRepo;

  @Parameter(property = "githubUrl", defaultValue = "api.github.com")
  private String githubUrl;

  @Parameter(property = "githubPrNumber")
  private int githubPrNumber;

  @Parameter(property = "prArtifactsPath")
  private String prArtifactsPath;

  @Parameter(property = "projectRootName")
  private String projectRootName;

  public AbstractPitMojo() {
    this(new RunPitStrategy(), new DependencyFilter(new PluginServices(
        AbstractPitMojo.class.getClassLoader())), new PluginServices(
        AbstractPitMojo.class.getClassLoader()), new NonEmptyProjectCheck());
  }

  public AbstractPitMojo(final GoalStrategy strategy, final Predicate<Artifact> filter,
      final PluginServices plugins, final Predicate<MavenProject> emptyProjectCheck) {
    this.goalStrategy = strategy;
    this.filter = filter;
    this.plugins = plugins;
    this.notEmptyProject = emptyProjectCheck;
  }

  @Override
  public final void execute() throws MojoExecutionException,
      MojoFailureException {

    switchLogging();
    RunDecision shouldRun = shouldRun();

    if (shouldRun.shouldRun()) {
      for (final ToolClasspathPlugin each : this.plugins
          .findToolClasspathPlugins()) {
        this.getLog().info("Found plugin : " + each.description());
      }

      for (final ClientClasspathPlugin each : this.plugins
          .findClientClasspathPlugins()) {
        this.getLog().info(
            "Found shared classpath plugin : " + each.description());
      }

      final Optional<CombinedStatistics> result = analyse();
      if (result.isPresent()) {
        throwErrorIfTestStrengthBelowThreshold(result.get().getMutationStatistics());
        throwErrorIfScoreBelowThreshold(result.get().getMutationStatistics());
        throwErrorIfMoreThanMaximumSurvivors(result.get().getMutationStatistics());
        throwErrorIfCoverageBelowThreshold(result.get().getCoverageSummary());
      }

    } else {
      this.getLog().info("Skipping project because:");
      for (String reason : shouldRun.getReasons()) {
        this.getLog().info("  - " + reason);
      }
    }

    if (isLastProjectInReactor()) {
      this.getLog().info("Signal listeners after the whole project");
      signalLastProjectInBuild();
    }

  }

  public void setMavenSession(MavenSession session) {
    this.session = session;
  }

  public String getPrArtifactsPath() {
    return prArtifactsPath;
  }

  public void setPrArtifactsPath(String prArtifactsPath) {
    this.prArtifactsPath = prArtifactsPath;
  }

  private void signalLastProjectInBuild() {
    final ReportOptions data = new MojoToReportOptionsConverter(this,
        new SurefireConfigConverter(), this.filter).convert();
    EntryPoint e = new EntryPoint();
    e.signalAfterTheWholeBuild(detectBaseDir(), data, plugins);
  }

  public String getGithubToken() {
    return githubToken;
  }

  private void switchLogging() {
    if (this.useSlf4j) {
      SLF4JBridgeHandler.removeHandlersForRootLogger();
      SLF4JBridgeHandler.install();
      Logger.getLogger("PIT").addHandler(new SLF4JBridgeHandler());
      SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();
    }
  }

  private void throwErrorIfCoverageBelowThreshold(
      final CoverageSummary coverageSummary) throws MojoFailureException {
    if ((this.coverageThreshold != 0)
        && (coverageSummary.getCoverage() < this.coverageThreshold)) {
      throw new MojoFailureException("Line coverage of "
          + coverageSummary.getCoverage() + "("
          + coverageSummary.getNumberOfCoveredLines() + "/"
          + coverageSummary.getNumberOfLines() + ") is below threshold of "
          + this.coverageThreshold);
    }
  }

  private void throwErrorIfScoreBelowThreshold(final MutationStatistics result)
      throws MojoFailureException {
    if ((this.mutationThreshold != 0)
        && (result.getPercentageDetected() < this.mutationThreshold)) {
      throw new MojoFailureException("Mutation score of "
          + result.getPercentageDetected() + " is below threshold of "
          + this.mutationThreshold);
    }
  }

  private void throwErrorIfTestStrengthBelowThreshold(final MutationStatistics result)
          throws MojoFailureException {
    if ((this.testStrengthThreshold != 0)
            && (result.getTestStrength() < this.testStrengthThreshold)) {
      throw new MojoFailureException("Test strength score of "
              + result.getTestStrength() + " is below threshold of "
              + this.testStrengthThreshold);
    }
  }
  
  private void throwErrorIfMoreThanMaximumSurvivors(final MutationStatistics result)
      throws MojoFailureException {
    if ((this.maxSurviving >= 0)
        && (result.getTotalSurvivingMutations() > this.maxSurviving)) {
      throw new MojoFailureException("Had "
          + result.getTotalSurvivingMutations() + " surviving mutants, but only "
          + this.maxSurviving + " survivors allowed");
    }
  }

  protected Optional<CombinedStatistics> analyse() throws MojoExecutionException {
    final ReportOptions data = new MojoToReportOptionsConverter(this,
        new SurefireConfigConverter(), this.filter).convert();
    return Optional.ofNullable(this.goalStrategy.execute(detectBaseDir(), data,
        this.plugins, this.environmentVariables));
  }

  protected File detectBaseDir() {
    // execution project doesn't seem to always be available.
    // possibily a maven 2 vs maven 3 issue?
    final MavenProject executionProject = this.project.getExecutionProject();
    if (executionProject == null) {
      return null;
    }
    return executionProject.getBasedir();
  }

  protected Predicate<Artifact> getFilter() {
    return filter;
  }

  protected GoalStrategy getGoalStrategy() {
    return goalStrategy;
  }

  protected PluginServices getPlugins() {
    return plugins;
  }

  public List<String> getTargetClasses() {
    if ((targetClasses == null || targetClasses.isEmpty()) && !StringUtil.isNullOrEmpty(targetClassesCommaSep)) {
      return Arrays.stream(targetClassesCommaSep.split(",")).collect(Collectors.toList());
    }
    return withoutNulls(this.targetClasses);
  }

  public void setTargetClasses(ArrayList<String> targetClasses) {
    this.targetClasses = targetClasses;
  }

  public void setTargetClassesCommaSep(String targetClasses) {
    this.targetClassesCommaSep = targetClasses;
  }

  public List<String> getTargetTests() {
    if ((targetTests == null || targetTests.isEmpty()) && !StringUtil.isNullOrEmpty(targetTestsCommaSep)) {
      return Arrays.stream(targetTestsCommaSep.split(",")).collect(Collectors.toList());
    }
    return withoutNulls(this.targetTests);
  }

  public void setTargetTests(ArrayList<String> targetTests) {
    this.targetTests = targetTests;
  }

  public void setTargetTestsCommaSep(String targetTests) {
    this.targetTestsCommaSep = targetTests;
  }

  public List<String> getExcludedMethods() {
    return withoutNulls(this.excludedMethods);
  }

  public List<String> getExcludedClasses() {
    return withoutNulls(this.excludedClasses);
  }

  public List<String> getAvoidCallsTo() {
    return withoutNulls(this.avoidCallsTo);
  }

  public File getReportsDirectory() {
    return this.reportsDirectory;
  }

  public int getMaxDependencyDistance() {
    return this.maxDependencyDistance;
  }

  public int getThreads() {
    return this.threads;
  }

  public boolean isMutateStaticInitializers() {
    return this.mutateStaticInitializers;
  }

  public List<String> getMutators() {
    return withoutNulls(this.mutators);
  }

  public float getTimeoutFactor() {
    return this.timeoutFactor;
  }

  public long getTimeoutConstant() {
    return this.timeoutConstant;
  }

  public ArrayList<String> getExcludedTestClasses() {
    return withoutNulls(excludedTestClasses);
  }

  public int getMaxMutationsPerClass() {
    return this.maxMutationsPerClass;
  }

  public List<String> getJvmArgs() {
    return withoutNulls(this.jvmArgs);
  }

  public List<String> getOutputFormats() {
    return withoutNulls(this.outputFormats);
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  public MavenProject getProject() {
    return this.project;
  }

  public Map<String, Artifact> getPluginArtifactMap() {
    return this.pluginArtifactMap;
  }

  public boolean isFailWhenNoMutations() {
    return this.failWhenNoMutations;
  }

  public List<String> getExcludedGroups() {
    return withoutNulls(this.excludedGroups);
  }

  public List<String> getIncludedGroups() {
    return withoutNulls(this.includedGroups);
  }

  public List<String> getIncludedTestMethods() {
    return withoutNulls(this.includedTestMethods);
  }

  public boolean isFullMutationMatrix() {
    return fullMutationMatrix;
  }
  
  public void setFullMutationMatrix(boolean fullMutationMatrix) {
    this.fullMutationMatrix = fullMutationMatrix;
}

  public int getMutationUnitSize() {
    return this.mutationUnitSize;
  }

  public boolean isTimestampedReports() {
    return this.timestampedReports;
  }

  public boolean isDetectInlinedCode() {
    return this.detectInlinedCode;
  }

  public void setTimestampedReports(final boolean timestampedReports) {
    this.timestampedReports = timestampedReports;
  }

  public File getHistoryOutputFile() {
    return this.historyOutputFile;
  }

  public File getHistoryInputFile() {
    return this.historyInputFile;
  }

  public boolean isExportLineCoverage() {
    return this.exportLineCoverage;
  }

  protected RunDecision shouldRun() {
    RunDecision decision = new RunDecision();

    if (this.skip) {
      decision.addReason("Execution of PIT should be skipped.");
    }

    if (this.skipTests) {
      decision.addReason("Test execution should be skipped (-DskipTests).");
    }

    if ("pom".equalsIgnoreCase(this.project.getPackaging())) {
      decision.addReason("Packaging is POM.");
    }

    if (!notEmptyProject.test(project)) {
      decision.addReason("Project has no tests, it is empty.");
    }

    return decision;
  }

  public String getMutationEngine() {
    return this.mutationEngine;
  }

  public String getJavaExecutable() {
    return this.jvm;
  }

  public void setJavaExecutable(final String javaExecutable) {
    this.jvm = javaExecutable;
  }

  public List<String> getAdditionalClasspathElements() {
    return withoutNulls(this.additionalClasspathElements);
  }

  public List<String> getClasspathDependencyExcludes() {
    return withoutNulls(this.classpathDependencyExcludes);
  }

  public boolean isParseSurefireConfig() {
    return this.parseSurefireConfig;
  }

  public boolean skipFailingTests() {
    return this.skipFailingTests;
  }

  public Map<String, String> getPluginProperties() {
    return pluginConfiguration;
  }

  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  public boolean useHistory() {
    return this.withHistory;
  }

  public ArrayList<String> getExcludedRunners() {
    return withoutNulls(excludedRunners);
  }
  
  public ArrayList<String> getFeatures() {
    return withoutNulls(features);
  }

  public String getTestPlugin() {
    return testPlugin;
  }
   
  public boolean isUseClasspathJar() {
    return this.useClasspathJar;
  }

  static class RunDecision {
    private List<String> reasons = new ArrayList<>(4);

    boolean shouldRun() {
      return reasons.isEmpty();
    }

    public void addReason(String reason) {
      reasons.add(reason);
    }

    public List<String> getReasons() {
      return Collections.unmodifiableList(reasons);
    }
  }

  private <X> ArrayList<X> withoutNulls(List<X> originalList) {
    if (originalList == null) {
      return null;
    }

    return originalList.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  public String getBuildId() {
    return buildId;
  }

  public String getBuildTypeId() {
    return buildTypeId;
  }

  public String getHostname() {
    return hostname;
  }

  /**
   * Is this project the last project in the reactor?
   *
   * @return true if last project (including only project)
   */
  public boolean isLastProjectInReactor() {
    List<MavenProject> sortedProjects = session.getSortedProjects();

    MavenProject lastProject = sortedProjects.isEmpty()
        ? session.getCurrentProject()
        : sortedProjects.get(sortedProjects.size() - 1);

    if (getLog().isDebugEnabled()) {
      getLog().debug("Current project: '" + session.getCurrentProject().getName()
          + "', Last project to execute based on dependency graph: '" + lastProject.getName() + "'");
    }

    return session.getCurrentProject().equals(lastProject);
  }

  public String getGithubRepo() {
    return githubRepo;
  }

  public String getGithubUrl() {
    return githubUrl;
  }

  public int getGithubPrNumber() {
    return githubPrNumber;
  }

  public String getProjectRootName() {
    return projectRootName;
  }
}
