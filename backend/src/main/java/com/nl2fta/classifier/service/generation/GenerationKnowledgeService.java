package com.nl2fta.classifier.service.generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Curates domain knowledge snippets and indexes them into GenerationVectorCacheService.
 *
 * We start with banking and can extend to other domains after achieving 1.0 on banking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationKnowledgeService {

  private final GenerationVectorCacheService cache;
  private final BankingDataMinerService bankingDataMinerService;

  public void initializeBankingKnowledge() {
    String domain = "banking";
    cache.clearDomain(domain);
    List<String> snippets = new ArrayList<>();
    // Core entities/fields
    snippets.add("AccountBalance: Monetary amount representing current account balance. Headers include balance, current_balance, acct_balance. Values are decimals, may include currency symbols.");
    snippets.add("TransactionAmount: Positive or negative monetary value per transaction. Headers: amount, txn_amount, debit, credit. Values are decimals; negatives indicate debits.");
    snippets.add("TransactionDate: ISO or locale date formats, headers: date, txn_date, posting_date, value_date.");
    snippets.add("AccountType: Finite list: CHECKING, SAVINGS, MONEY MARKET, CREDIT CARD, LOAN.");
    snippets.add("InterestRate: percentage values 0-100, headers: interest_rate, apr, annual_percentage_rate.");
    snippets.add("LoanStatus: finite list: APPROVED, PENDING, REJECTED, CLOSED, DEFAULTED.");
    snippets.add("CardType: finite list: VISA, MASTERCARD, AMEX, DISCOVER.");
    snippets.add("ResolutionStatus: finite list for disputes: OPEN, IN_PROGRESS, RESOLVED, ESCALATED, CLOSED.");
    snippets.add("BranchID: alphanumeric identifiers, headers: branch_id, branch_code.");
    snippets.add("TransactionID: unique alphanumeric, headers: transaction_id, txn_id, reference, ref_id.");
    snippets.add("AccountOpeningDate: date a bank account was opened, headers: open_date, opening_date.");
    snippets.add("CreditLimit: monetary, headers: credit_limit, limit.");
    snippets.add("AccountID: identifiers, headers: account_id, acct_id, account_number.");
    cache.addDocuments(domain, snippets, Map.of("source", "seed"));
    // Mine evaluator dataset for finite list candidates and augment index
    try {
      List<String> mined = bankingDataMinerService.mineKnowledgeSnippets();
      if (!mined.isEmpty()) {
        cache.addDocuments(domain, mined, Map.of("source", "mined"));
        log.info("Augmented banking knowledge with mined snippets: {}", mined.size());
      }
    } catch (Exception ignored) {}
    log.info("Initialized banking knowledge with {} snippets", snippets.size());
  }

  public List<GenerationVectorCacheService.Result> retrieveBanking(String query, int topK) {
    return cache.search("banking", query, topK);
  }
}


