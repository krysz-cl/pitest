package org.pitest.mutationtest.report.github;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.pitest.coverage.CoverageDatabase;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.SourceLocator;
import org.pitest.mutationtest.config.DirectoryResultOutputStrategy;
import org.pitest.mutationtest.config.UndatedReportDirCreationStrategy;
import org.pitest.mutationtest.report.html.MutationTestSummaryData;
import org.pitest.mutationtest.report.html.PackageSummaryData;
import org.pitest.mutationtest.report.html.PackageSummaryMap;
import org.pitest.util.Log;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ExpressionContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class GitHubSingleReportListener implements MutationResultListener {

  private static final Logger LOG               = Log.getLogger();

  private GitTcParams params;
  private final Collection<SourceLocator> sourceRoots;
  private final CoverageDatabase          coverage;
  private final Set<String> mutatorNames;

  private TemplateEngine templateEngine;
  private final PackageSummaryMap packageSummaryData = new PackageSummaryMap();

  private final DirectoryResultOutputStrategy outputStrategy;

  public GitHubSingleReportListener(CoverageDatabase coverage,
      Collection<String> mutatorNames,
      GitTcParams params, SourceLocator... locators) {
    this.coverage = coverage;
    this.mutatorNames = new HashSet<>(mutatorNames);
    this.params = params;
    this.sourceRoots = new HashSet<>(Arrays.asList(locators));

    String baseDir = "./target/";
    this.outputStrategy = new DirectoryResultOutputStrategy(baseDir, new UndatedReportDirCreationStrategy());

    templateInit();
  }

  private void templateInit() {
    ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
    templateResolver.setTemplateMode(TemplateMode.TEXT);
    templateResolver.setPrefix("/templates/");
    templateResolver.setSuffix(".md");
    templateResolver.setCacheable(false);

    templateEngine = new TemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);
  }

  private PackageSummaryData collectPackageSummaries(
      final ClassMutationResults mutationMetaData) {
    String packageName = mutationMetaData.getPackageName();

    return this.packageSummaryData.update(packageName,
        createSummaryData(this.coverage, mutationMetaData));
  }

  public MutationTestSummaryData createSummaryData(
      final CoverageDatabase coverage, final ClassMutationResults data) {
    return new MutationTestSummaryGitData(data.getFileName(), data.getMutations(),
        this.mutatorNames, coverage.getClassInfo(Collections.singleton(data
        .getMutatedClass())), coverage.getNumberOfCoveredLines(Collections
        .singleton(data.getMutatedClass())), params);
  }

  @Override
  public void runStart() {

  }

  @Override
  public void handleMutationResult(final ClassMutationResults metaData) {
    collectPackageSummaries(metaData);
  }

  @Override
  public void runEnd() {
    renderReportPerModule();
  }

  private void renderReportPerModule() {
    final List<PackageSummaryData> psd = new ArrayList<>(
        this.packageSummaryData.values());

    if (psd.isEmpty()) {
      return;
    }

    Collections.sort(psd);

    PackageSummaryData wrapper = new PackageSummaryData("example");
    psd.stream().map(PackageSummaryData::getSummaryData).flatMap(Collection::stream).forEach(wrapper::addSummaryData);

    Map<String, Object> templateParams = new HashMap<>();
    templateParams.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
    templateParams.put("detail", wrapper);
    String binded = bind("part", templateParams);
    LOG.info(binded);

    final Writer writer = this.outputStrategy.createWriterForFile(
        params.getCurrentDirPath().replaceAll("/", ".") + ".md");
    try {
      writer.write(binded);
      writer.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

  private String bind(String templateName, Map<String, Object> templateParams) {
    return templateEngine.process(templateName, new ExpressionContext(templateEngine.getConfiguration(), Locale.US, templateParams));
  }
}
