package com.ayuget.redface.data.api.hfr.transforms;

import com.ayuget.redface.data.api.model.Post;
import com.ayuget.redface.ui.UIConstants;
import com.ayuget.redface.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.functions.Func1;

public class HTMLToPostList implements Func1<String, List<Post>> {
    private static final Pattern descriptionPattern = Pattern.compile(
            "(?:<meta name=\"Description\" content=\")(?:.*)(?:Pages : )(\\d+)(?:[^\"])"
    );

    private static final Pattern postPattern = Pattern.compile(
            "(<table\\s*cellspacing.*?class=\"([a-z]+)\">.*?" +
            "<tr.*?class=\"message.*?" +
            "<a.*?href=\"#t([0-9]+)\".*?" +
            "<b.*?class=\"s2\">(?:<a.*?>)?(.*?)(?:</a>)?</b>.*?" +
            "(?:(?:<div\\s*class=\"avatar_center\".*?><img src=\"(.*?)\"\\s*alt=\".*?\"\\s*/></div>)|</td>).*?" +
            "<div.*?class=\"left\">Posté le ([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+):([0-9]+).*?" +
            "<div.*?id=\"para[0-9]+\">(.*?)<div style=\"clear: both;\">\\s*</div></p>" +
            "(?:<div\\s*class=\"edited\">)?(?:<a.*?>Message cité ([0-9]+) fois</a>)?(?:<br\\s*/>Message édité par .*? le ([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+):([0-9]+)</div>)?.*?" +
            "</div></td></tr></table>)"
            , Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public List<Post> call(String source) {
        List<Post> posts = new ArrayList<>();

        // Description tag parsing to find the total number of pages. If
        int topicPagesCount = UIConstants.UNKNOWN_PAGES_COUNT;
        Matcher pagesMatcher = descriptionPattern.matcher(source);

        if (pagesMatcher.find()) {
            topicPagesCount = Integer.valueOf(pagesMatcher.group(1));
        }

        Matcher m = postPattern.matcher(source);

        while (m.find()) {
            long postId = Long.parseLong(m.group(3));
            String postHTMLContent = m.group(12);
            Date postDate = DateUtils.fromHTMLDate(m.group(8), m.group(7), m.group(6), m.group(9), m.group(10), m.group(11));
            Date lastEditDate = null;
            int quoteCount = 0;
            String author = m.group(4);
            String avatarUrl = m.group(5);
            boolean wasEdited = m.group(14) != null;
            boolean wasQuoted = m.group(13) != null;

            if (wasEdited) {
                lastEditDate = DateUtils.fromHTMLDate(m.group(16), m.group(15), m.group(14), m.group(17), m.group(18), m.group(19));
            }

            if (wasQuoted) {
                quoteCount = Integer.parseInt(m.group(13));
            }

            Post post = new Post(postId);
            post.setHtmlContent(postHTMLContent);
            post.setAuthor(author);
            post.setAvatarUrl(avatarUrl);
            post.setLastEditionDate(lastEditDate);
            post.setPostDate(postDate);
            post.setQuoteCount(quoteCount);

            if (topicPagesCount != UIConstants.UNKNOWN_PAGES_COUNT) {
                post.setTopicPagesCount(topicPagesCount);
            }

            posts.add(post);
        }

        return posts;
    }
}
