package com.shepherdmoney.interviewproject.controller;

import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.shepherdmoney.interviewproject.repository.*;
import com.shepherdmoney.interviewproject.model.*;


@RestController
public class CreditCardController {

    // wire in CreditCard repository here (~1 line)
    @Autowired
    private CreditCardRepository creditCardRepository;
    @Autowired
    private UserRepository userRepository;

    @PostMapping("/credit-card")
    public ResponseEntity<Integer> addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {
        // Create a credit card entity, and then associate that credit card with user with given userId
        //       Return 200 OK with the credit card id if the user exists and credit card is successfully associated with the user
        //       Return other appropriate response code for other exception cases
        //       Do not worry about validating the card number, assume card number could be any arbitrary format and length
        int userId = payload.getUserId();
        // generate a new credit card entity
        CreditCard newCard = new CreditCard();
        newCard.setIssuanceBank(payload.getCardIssuanceBank());
        newCard.setNumber(payload.getCardNumber());
        // save the new credit card entity if the user exists
        return userRepository.findById(userId).map(user -> {
            newCard.setOwner(user);
            CreditCard savedCard = creditCardRepository.save(newCard);
            // return the id of the saved credit card if the user exists
            return ResponseEntity.ok(savedCard.getId());
            // return fail message if the user does not exist
        }).orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {
        // return a list of all credit card associated with the given userId, using CreditCardView class
        //       if the user has no credit card, return empty list, never return null
        return userRepository.findById(userId).map(user -> {
            // get the list of credit cards associated with the user
            List<CreditCard> cards = user.getCreditCards();
            // create a list of credit card views
            List<CreditCardView> creditCardViews = new ArrayList<>();
            for (CreditCard card : cards) {
                CreditCardView tempCardView = new CreditCardView(card.getIssuanceBank(), card.getNumber());
                creditCardViews.add(tempCardView);
            }
            // return the list of credit card views if the user exists
            return ResponseEntity.ok(creditCardViews);
            // return fail message if the user does not exist
        }).orElse(ResponseEntity.ok(List.of()));
    }

    @GetMapping("/credit-card:user-id")
    public ResponseEntity<Integer> getUserIdForCreditCard(@RequestParam String creditCardNumber) {
        CreditCard card = creditCardRepository.findByNumber(creditCardNumber).orElse(null);
        if (card == null) {
            // return fail message if the credit card does not exist
            return ResponseEntity.badRequest().build();
        } else {
            // return the user id of the credit card if the credit card exists
            return ResponseEntity.ok(card.getOwner().getId());
        }
    }

    @PostMapping("/credit-card:update-balance")
    public ResponseEntity<Integer> updateBalanceHistory(@RequestBody UpdateBalancePayload[] payload) {
        // Given a list of transactions, update credit cards' balance history.
        //      1. For the balance history in the credit card
        //      2. If there are gaps between two balance dates, fill the empty date with the balance of the previous date
        //      3. Given the payload `payload`, calculate the balance different between the payload and the actual balance stored in the database
        //      4. If the different is not 0, update all the following budget with the difference
        //      For example: if today is 4/12, a credit card's balanceHistory is [{date: 4/12, balance: 110}, {date: 4/10, balance: 100}],
        //      Given a balance amount of {date: 4/11, amount: 110}, the new balanceHistory is
        //      [{date: 4/12, balance: 120}, {date: 4/11, balance: 110}, {date: 4/10, balance: 100}]
        //      Return 200 OK if update is done and successful, 400 Bad Request if the given card number
        //        is not associated with a card.

        // iterate through the payload
       for (UpdateBalancePayload load : payload) {
           // get the credit card entity with the given card number
            CreditCard card = creditCardRepository.findByNumber(load.getCreditCardNumber()).orElse(null);
            // if the credit card does not exist, return fail message
            if (card == null) {
                return ResponseEntity.badRequest().build();
            }
            LocalDate loadDate = load.getBalanceDate();
            List<BalanceHistory> balanceHistories = card.getBalanceHistories();
            if (balanceHistories.size() == 0) {
                // creat a new record
                BalanceHistory newRecord = new BalanceHistory();
                newRecord.setDate(loadDate);
                newRecord.setBalance(load.getBalanceAmount());
                // add the new record to the balance history of the credit card
                balanceHistories.add(newRecord);
                return ResponseEntity.ok().build();
            }
            // fill the gaps in the balance history of the credit card
            balanceHistories = fillBalanceGaps(balanceHistories);
            LocalDate lastDate = balanceHistories.get(0).getDate();
            // if the load date is before the first date in the balance history
            if (loadDate.isBefore(lastDate)) {
                // add new records to the balance history of the credit card and fill the gap
                while (loadDate.isBefore(lastDate.plusDays(1))) {
                    lastDate = lastDate.minusDays(1);
                    BalanceHistory newRecord = new BalanceHistory();
                    newRecord.setDate(lastDate);
                    newRecord.setBalance(load.getBalanceAmount());
                }
                // if the load date is after the last date in the balance history
            } else if (loadDate.isAfter(lastDate)) {
                // add new records to the balance history of the credit card and fill the gap
                while (loadDate.isAfter(lastDate)) {
                    lastDate = lastDate.plusDays(1);
                    BalanceHistory newRecord = new BalanceHistory();
                    newRecord.setDate(lastDate);
                    newRecord.setBalance(balanceHistories.get(balanceHistories.size() - 1).getBalance());
                }
                // if the load date is in the balance history
            } else {
                // find the record with the load date and update the balance
                for (BalanceHistory history : balanceHistories) {
                    if (history.getDate().equals(loadDate)) {
                        history.setBalance(load.getBalanceAmount());
                        break;
                    }
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    private List<BalanceHistory> fillBalanceGaps (List<BalanceHistory> balanceHistories) {
        // a helper function to fill the gaps in the balance history of a credit card
        // return a new list of balance history with the gaps filled
        List<BalanceHistory> newBalanceHistories = new ArrayList<>();
        if (balanceHistories.size() == 0) {
            return newBalanceHistories;
        }
        LocalDate lastDate = balanceHistories.get(0).getDate();
        newBalanceHistories.add(balanceHistories.get(0));
        for (int i = 1; i < balanceHistories.size(); i++) {
            LocalDate currentDate = balanceHistories.get(i).getDate();
            while (lastDate.isBefore(currentDate.minusDays(1))) {
                lastDate = lastDate.plusDays(1);
                // set a new balance record with the previous balance
                BalanceHistory newRecord = new BalanceHistory();
                newRecord.setDate(lastDate);
                newRecord.setBalance(balanceHistories.get(i - 1).getBalance());
                newBalanceHistories.add(i, newRecord);
            }
        }
        return newBalanceHistories;
    }
    
}
