/*
	Copyright 2018 NAVER Corp.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */
package org.pitest.mutationtest.report.github.reporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Predicate;

import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.User;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.report.github.GitTcParams;
import org.pitest.mutationtest.report.github.manager.GithubCommentManager;
import org.pitest.mutationtest.report.github.manager.GithubPullRequestManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubPullRequestReporter implements MutationResultListener {
    private static final Logger logger = LoggerFactory.getLogger(GithubPullRequestReporter.class);

    private static final String REPORT_HEADER = "[PIT Mutation Coverage]\n\n";

    private static final String REPORT_TABLE_HEADER =
          "|   |path|line coverage|mutation coverage|test strength|\n"
        + "|----|----|----|----|----|";

    private GithubPullRequestManager manager;
    private GithubCommentManager commentManager;

    public GithubPullRequestReporter(GitTcParams params) {
        githubInit(params);
    }

    @Override
    public void runStart() {

    }

    @Override
    public void handleMutationResult(ClassMutationResults results) {

    }

    @Override
    public void runEnd() {

    }

    private void githubInit(GitTcParams params) {
        this.manager = new GithubPullRequestManager(params.getGithubUrl(), params.getGithubToken(),
            params.getGithubRepo(), params.getGithubPrNumber());
        this.commentManager = manager.commentManager();
    }

    @Override
    public void runAfterWholeBuild() {
        try {
            String content = getComment();
            String comment = REPORT_HEADER + "\n";

            if (content.isEmpty()) {
                comment += "PIT Mutation report is empty";
            } else {
               comment += REPORT_TABLE_HEADER + "\n" + content;
            }

            logger.debug("result comment {}", comment);

            User watcher = manager.getUser();
            commentManager.deleteComment(oldReport(watcher));
            commentManager.addComment(comment);
            logger.debug("add comment {}", comment);

            //statusManager.setStatus(commitStatus);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getComment() throws IOException {
        StringBuilder builder = new StringBuilder();
        File dir = new File("./target");
        String[] fileNames = dir.list();

        if (fileNames == null) {
            return "";
        }

        for (String fileName : fileNames) {
            if (!fileName.endsWith("md")) {
                continue;
            }
            File f = new File(dir, fileName);
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line = br.readLine();
            while (line != null) {
                builder.append(line).append("\n");
                line = br.readLine();
            }
            if (builder.length() > 0) {
                builder.setLength(builder.length() - 1);
            }
        }
        return builder.toString();
    }

    Predicate<Comment> oldReport(User watcher) {
        return c -> c.getUser().getId() == watcher.getId() && c.getBody().contains(REPORT_HEADER);
    }

}
