package com.bank.messaging.consultation.api;

import com.bank.messaging.consultation.application.MessageDetails;
import com.bank.messaging.consultation.application.MessageFilter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import com.bank.messaging.consultation.application.MessagePage;
import com.bank.messaging.consultation.application.QueryMessagesUseCase;
import com.bank.messaging.consultation.application.InvalidMessageQueryException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final QueryMessagesUseCase queryService;

    public MessageController(QueryMessagesUseCase queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<MessagePage> getMessages(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String sourceQueue,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.getPage(parseInteger(page, 0), parseInteger(size, 20),
                        new MessageFilter(messageId, sourceQueue, parseInstant(from), parseInstant(to), ascending(sort))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageDetails> getMessage(@PathVariable long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.getMessage(id));
    }

    private boolean ascending(String sort) {
        if (sort == null || sort.equals("receivedAt,desc")) return false;
        if (sort.equals("receivedAt,asc")) return true;
        throw new InvalidMessageQueryException("Tri accepté : receivedAt,asc ou receivedAt,desc.");
    }

    private Instant parseInstant(String value) {
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidMessageQueryException("Les dates doivent être des instants ISO-8601 avec fuseau.");
        }
    }

    private int parseInteger(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidMessageQueryException("Les paramètres de pagination doivent être des entiers.");
        }
    }
}
