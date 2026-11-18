package com.aeroflow.checkin.api;

import com.aeroflow.checkin.api.CheckinDtos.*;
import com.aeroflow.checkin.domain.Checkin;
import com.aeroflow.checkin.service.BoardingPassRenderer;
import com.aeroflow.checkin.service.BookingClient;
import com.aeroflow.checkin.service.CheckinService;
import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkin")
@Tag(name = "Check-in")
public class CheckinController {
    private final CheckinService service;
    private final BoardingPassRenderer renderer;

    public CheckinController(CheckinService service, BoardingPassRenderer renderer) {
        this.service = service;
        this.renderer = renderer;
    }

    @PostMapping("/retrieve")
    @Operation(summary = "Retrieve booking for check-in (PNR + last name)")
    public CheckinView retrieve(@Valid @RequestBody RetrieveRequest req) {
        return service.retrieve(req.pnr(), req.lastName());
    }

    @PutMapping("/seat")
    public BookingClient.Booking seat(@Valid @RequestBody SeatRequest req) {
        return service.changeSeat(req);
    }

    @PostMapping("/complete")
    @Operation(summary = "Check in selected passengers and issue boarding passes")
    public CheckinView complete(@Valid @RequestBody CompleteRequest req) {
        return service.complete(req);
    }

    @PostMapping(value = "/boarding-pass", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a boarding pass as PDF")
    public ResponseEntity<byte[]> boardingPass(@Valid @RequestBody BoardingPassRequest req) {
        Checkin c = service.boardingPass(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("boarding-pass-" + c.getBoardingPassNumber() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(renderer.render(c));
    }

    @GetMapping
    @PreAuthorize(Roles.IS_STAFF)
    public PageResponse<BoardingPass> list(@RequestParam(required = false) UUID flightId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(flightId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by("checkedInAt").descending()));
    }
}
