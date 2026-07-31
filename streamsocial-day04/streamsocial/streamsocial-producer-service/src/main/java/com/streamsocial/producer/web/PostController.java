package com.streamsocial.producer.web;

import com.streamsocial.producer.service.PostProducerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class PostController {

    private final PostProducerService postProducerService;

    public PostController(PostProducerService postProducerService) {
        this.postProducerService = postProducerService;
    }

    @PostMapping("/api/posts")
    public CompletableFuture<ResponseEntity<PostCreatedResponse>> createPost(@Valid @RequestBody CreatePostRequest request) {
        return postProducerService.publish(request)
                .thenApply(result -> ResponseEntity.accepted().body(new PostCreatedResponse(
                        result.event().eventId(),
                        result.event().postId(),
                        result.event().occurredAt(),
                        result.metadata().partition(),
                        result.metadata().offset())));
    }
}
