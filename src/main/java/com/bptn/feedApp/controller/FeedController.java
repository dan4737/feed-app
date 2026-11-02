package com.bptn.feedApp.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.bptn.feedApp.service.FeedService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.bptn.feedApp.jpa.Feed;
import com.bptn.feedApp.domain.PageResponse;

/* Every route here is protected: /feeds is not in jwt.excluded.urls,
   so a Bearer token is required on all of them. */
@CrossOrigin
@RestController
@RequestMapping("/feeds")
public class FeedController {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    FeedService feedService;

    /* No path on the mapping, so this answers POST /feeds itself. */
    @PostMapping
    public Feed createFeed(@RequestBody Feed feed) {

        logger.debug("Creating Feed");

        return this.feedService.createFeed(feed);
    }

    /* Spring converts the path segment to an int for us. A non-numeric
       value like /feeds/abc fails that conversion and never reaches this method. */
    @GetMapping("/{feedId}")
    public Feed getFeed(@PathVariable int feedId) {

        logger.debug("Getting Feed, feedId: {}", feedId);

        return this.feedService.getFeedById(feedId);
    }

    /* Two path segments, so /feeds/user/0/5 cannot collide with /feeds/{feedId}. */
    @GetMapping("/user/{pageNum}/{pageSize}")
    public PageResponse<Feed> getUserFeeds(@PathVariable int pageNum, @PathVariable int pageSize) {

        logger.debug("Getting User Feeds List, pageNum: {}, pageSize: {}", pageNum, pageSize);

        return this.feedService.getUserFeeds(pageNum, pageSize);
    }

}
