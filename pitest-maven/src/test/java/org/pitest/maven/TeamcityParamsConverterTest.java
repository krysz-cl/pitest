package org.pitest.maven;

import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TeamcityParamsConverterTest {

  private AbstractPitMojo mojo;

  // Example Project Structure:
  // null/spring-boot-starter-parent/tax-generic-bridge/generic-bridge/
  @Before
  public void setUp() {
    mojo = mock(AbstractPitMojo.class);

    MavenProject root = mock(MavenProject.class);
    MavenProject parent = mock(MavenProject.class);
    MavenProject project = mock(MavenProject.class);

    when(root.getArtifactId()).thenReturn("spring-boot-starter-parent");
    when(parent.getArtifactId()).thenReturn("tax-generic-bridge");
    when(project.getArtifactId()).thenReturn("generic-bridge");

    when(root.getParent()).thenReturn(null);
    when(parent.getParent()).thenReturn(root);
    when(project.getParent()).thenReturn(parent);

    when(mojo.getProject()).thenReturn(project);
  }

  @Test
  public void wholePathForCurrentDir() {
    //given
    TeamcityParamsConverter sut = new TeamcityParamsConverter();

    //when
    String currentDirPath = sut.computeCurrentDirPath(mojo);

    //then
    assertEquals("tax-generic-bridge/generic-bridge", currentDirPath);
  }

  @Test
  public void currentDirRelativeToProjectRootName() {
    //given
    when(mojo.getProjectRootName()).thenReturn("tax-generic-bridge");
    TeamcityParamsConverter sut = new TeamcityParamsConverter();

    //when
    String currentDirPath = sut.computeCurrentDirPath(mojo);

    //then
    assertEquals("generic-bridge", currentDirPath);
  }

}
