package com.bptn.feedApp.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.bptn.feedApp.repository.FeedRepository;
import com.bptn.feedApp.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bptn.feedApp.exception.domain.UserNotFoundException;
import com.bptn.feedApp.exception.domain.FeedNotFoundException;
import com.bptn.feedApp.jpa.Feed;
import com.bptn.feedApp.jpa.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.bptn.feedApp.domain.PageResponse;
import java.util.Optional;
import com.bptn.feedApp.exception.domain.LikeExistException;
import com.bptn.feedApp.exception.domain.FeedNotUserException;
import com.bptn.feedApp.jpa.FeedMetaData;
import com.bptn.feedApp.repository.FeedMetaDataRepository;

@Service
public class FeedService {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    UserRepository userRepository;

    @Autowired
    FeedRepository feedRepository;

    @Autowired
    FeedMetaDataRepository feedMetaDataRepository;

    public Feed createFeed(Feed feed) {

        /* The owner of the feed is whoever holds the JWT, not whatever the client sends. */
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

        feed.setUser(user);
        feed.setCreatedOn(Timestamp.from(Instant.now()));

        return this.feedRepository.save(feed);
    }

    /* findById comes free with JpaRepository, no custom query needed. */
    public Feed getFeedById(int feedId) {

        return this.feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));
    }

    public PageResponse<Feed> getUserFeeds(int pageNum, int pageSize) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

        /* PageRequest says which slice to fetch: page number (zero based), how many
           per page, and the order. Newest feeds first, since ids only go up. */
        Page<Feed> paged = this.feedRepository.findByUser(user, PageRequest.of(pageNum, pageSize, Sort.by("feedId").descending()));

        return new PageResponse<Feed>(paged);
    }

    /* Same as getUserFeeds, but findByUserNot flips the condition:
       every feed except the ones posted by the caller. */
    public PageResponse<Feed> getOtherUsersFeeds(int pageNum, int pageSize) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

        Page<Feed> paged = this.feedRepository.findByUserNot(user, PageRequest.of(pageNum, pageSize, Sort.by("feedId").descending()));

        return new PageResponse<Feed>(paged);
    }

    /* One FeedMetaData row is either a like or a comment, never both.
       The client sends isLike=true for a like, or a comment string for a comment. */
    public FeedMetaData createFeedMetaData(int feedId, FeedMetaData meta) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

        Feed feed = this.feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));

        /* Build a fresh object rather than trusting the one from the client,
           so the user, feed and timestamp are always set by the server. */
        FeedMetaData newMeta = new FeedMetaData();

        newMeta.setIsLike(false);
        newMeta.setUser(user);
        newMeta.setFeed(feed);
        newMeta.setCreatedOn(Timestamp.from(Instant.now()));

        if (Optional.ofNullable(meta.getIsLike()).isPresent()) {

            newMeta.setIsLike(meta.getIsLike());

            if (meta.getIsLike()) {

                /* A user may like a feed only once. Scan the feed's existing
                   metadata for a like by this same user. */
                feed.getFeedMetaData().stream()
                        .filter(m -> m.getUser().getUsername().equals(username))
                        .filter(m -> m.getIsLike().equals(true)).findAny()
                        .ifPresent(m -> {throw new LikeExistException(String.format("Feed already liked, feedId: %d, username: %s", feedId, username));});

                newMeta.setComment("");
            }
        }

        /* Not a like, so it must be a comment. */
        if (!newMeta.getIsLike()) {
            newMeta.setComment(meta.getComment());
        }

        return this.feedMetaDataRepository.save(newMeta);
    }

    public void deleteFeed(int feedId) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Feed feed = this.feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException(String.format("Feed doesn't exist, %d", feedId)));

        /* Only the owner may delete. The filter drops the feed if the usernames differ,
           and orElseThrow then fires. */
        Optional.of(feed).filter(f -> f.getUser().getUsername().equals(username))
                .orElseThrow(() -> new FeedNotUserException(String.format("Feed doesn't belong to current User, feedId: %d, username: %s", feedId, username)));

        /* Cascade ALL on Feed.feedMetaData removes the likes and comments with it. */
        this.feedRepository.delete(feed);
    }

}
