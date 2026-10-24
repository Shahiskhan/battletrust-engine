package com.bte.controller;

import com.bte.dto.MatchResultResponse;
import com.bte.dto.MatchResultSubmitRequest;
import com.bte.service.MatchResultService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/leader/match-results", "/api/v1/organizer/winner-portal"})
@org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER','ROLE_ADMIN')")
public class LeaderMatchResultController {
    @org.springframework.web.bind.annotation.PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public MatchResultResponse revise(@org.springframework.web.bind.annotation.PathVariable Long id,@Valid @RequestPart("result")MatchResultSubmitRequest request,@RequestPart("proofImages")List<MultipartFile> proof,@RequestPart("reason")String reason,Authentication auth){return matchResultService.revise(id,auth.getName(),request,proof,reason);}

	private final MatchResultService matchResultService;

	public LeaderMatchResultController(MatchResultService matchResultService) {
		this.matchResultService = matchResultService;
	}

	@PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_ORGANIZER')")
	public ResponseEntity<MatchResultResponse> submit(
			@Valid @RequestPart("result") MatchResultSubmitRequest request,
			@RequestPart("proofImages") List<MultipartFile> proofImages,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(matchResultService.submit(authentication.getName(), request, proofImages));
	}
}
