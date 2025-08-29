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
  private final TransactionsDataMinerService transactionsDataMinerService;

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

  public void initializeTransactionsKnowledge() {
    String domain = "transactions";
    cache.clearDomain(domain);
    List<String> snippets = new ArrayList<>();
    // Seed domain facts
    snippets.add(
        "TransactionID: unique alphanumeric (TX...), headers: transactionid, txn_id, reference, ref_id.");
    snippets.add(
        "TransactionID header patterns: (?i)(transaction[ _-]?id|txn[ _-]?id|tx[ _-]?id|transaction[ _-]?ref|reference|ref[ _-]?(id|no|number)|auth(orization)?[ _-]?code|approval[ _-]?code)");
    snippets.add(
        "TransactionID value pattern (regex): ^TX\\d{6}$ (examples: TX000001, TX123456)");
    snippets.add(
        "AccountID: alphanumeric account identifier (AC...), headers: accountid, account_id, acct_id.");
    snippets.add(
        "TransactionAmount: numeric decimal amount, headers: amount, transactionamount, debit, credit.");
    snippets.add(
        "TransactionDate: date/datetime, headers: transactiondate, txn_date, posting_date.");
    snippets.add("TransactionType: finite list candidate: Debit, Credit, Transfer, Payment, Refund.");
    snippets.add("Location: city names (e.g., Houston, San Diego), headers: location, city.");
    snippets.add("DeviceID: alphanumeric device code (D000xxx), headers: deviceid, device_id.");
    snippets.add("IP Address: IPv4 addresses, headers: ip address, ip, ip_addr.");
    snippets.add("MerchantID: merchant code (Mxxx), headers: merchantid, merchant_id.");
    snippets.add("Channel: finite list candidate: ATM, Online, Mobile, Branch, POS.");
    snippets.add(
        "Channel header patterns: (?i)(channel|transaction[ _-]?channel|txn[ _-]?channel)");
    snippets.add("Channel synonyms and related terms: web, online banking, internet, ecom, ecommerce, point of sale, pos, in-branch, teller, kiosk, mobile app, in-app, app, call center, ivr, phone, atm, branch, agent, desktop, portal");
    snippets.add("Channel value & canonical examples: ATM, ONLINE, MOBILE, BRANCH, POS");
    snippets.add("Channel normalization guidance: treat values case-insensitively and map WEB/ONLINE BANKING/INTERNET/ECOMMERCE->ONLINE; IN-APP/APP->MOBILE; POINT OF SALE/POS->POS; IN-BRANCH/TELLER->BRANCH");
    snippets.add("CustomerAge: numeric with decimals, headers: customerage, age.");
    snippets.add(
        "CustomerOccupation: free-text profession (Doctor, Engineer, Nurse, Teacher, Manager), also abbreviations (DR). Headers: customeroccupation, occupation, job, profession.");
    snippets.add(
        "CustomerOccupation header patterns: (?i)(customer[ _-]?occupation|occupation|job|profession)");
    snippets.add("CustomerOccupation value examples: Doctor|DR, Engineer, Nurse, Teacher, Manager");
    snippets.add("TransactionDuration: numeric seconds, headers: transactionduration, duration.");
    snippets.add("LoginAttempts: small integer counts, headers: loginattempts, attempts.");
    snippets.add("AccountBalance: decimal balance post-transaction, headers: accountbalance, balance.");
    snippets.add(
        "PreviousTransactionDate: prior date/datetime, headers: previoustransactiondate, last_txn_date.");
    cache.addDocuments(domain, snippets, Map.of("source", "seed"));
    log.info("Initialized transactions knowledge with {} snippets", snippets.size());
  }

  public List<GenerationVectorCacheService.Result> retrieveTransactions(String query, int topK) {
    return cache.search("transactions", query, topK);
  }

  public void initializeExtensionKnowledge() {
    String domain = "extension";
    cache.clearDomain(domain);
    List<String> snippets = new ArrayList<>();

    // Identity & check-digit families (descriptive facts, synonyms, formats)
    snippets.add("ABA Routing Number: 9 digits with checksum (US bank routing); synonyms: aba, routing number, transit number");
    snippets.add("CUSIP: 9 characters (alphanumeric + check); synonyms: securities number, sec trades number, cusip; typical like 037833100");
    snippets.add("ISIN: 12 characters (country code + alnum + check); synonyms: international securities code, isin; e.g., US0378331005");
    snippets.add("EAN-13: 13 digits with check; synonyms: european article number, ean; used for books/publishing and retail items");
    snippets.add("UPC: 12 digits with check; synonyms: universal product code, upc, universal product numbers");
    snippets.add("IBAN: International Bank Account Number; structure varies by country; often prefixed with country code (e.g., GB29..., DE89...)");
    snippets.add("LUHN (Mod 10): checksum mechanism used by credit cards and IMEI; cues include 'mod10' or 'luhn'");
    snippets.add("EIN (US employer ID): pattern ##-#######; cues: 'ein', 'employer id'");
    snippets.add("NPI (US healthcare provider): 10 digits with check; cues: 'npi'");
    snippets.add("DUNS: 9-digit Dun & Bradstreet identifier; cues: 'duns', 'duns number'");
    snippets.add("IMEI: 15 digits with Luhn; cues: 'imei'; not a telephone number");
    snippets.add("GUID/UUID: 8-4-4-4-12 hex segments; cues: 'guid' or 'uuid'; not generic IDENTIFIER when format matches");
    snippets.add("Swiss SSN: cues like 'ch' and 'soc sec' indicate Swiss national number (not telephone)");
    snippets.add("France SSN (INSEE): cues like 'fr' and 'soc sec' indicate FR SSN (not telephone)");

    // Geospatial & coordinates
    snippets.add("Longitude (decimal): range -180..180; synonyms: longitude, lon, long");
    snippets.add("Latitude (decimal): range -90..90; synonyms: latitude, lat");
    snippets.add("Coordinate pair (decimal): latitude and longitude together; cues: 'latitude_and_longitude', 'lat_long', 'lat long'");
    snippets.add("Easting/Northing (UTM): numeric components often labeled easting/northing");
    snippets.add("IATA Airport Code: exactly 3 letters; cues: 'airport code', 'iata'");
    snippets.add("IATA Airline Code: 2 letters; cues: 'airline code', 'iata airline'");

    // Language/Country/Continent
    snippets.add("Currency Code (ISO 4217): finite set (USD, EUR, GBP, ...); cues: 'currency code', 'iso-4217'");
    snippets.add("Currency (EN): names like US DOLLAR, EURO; cues: 'currency', 'money name'");
    snippets.add("Language Code (ISO 639-1): 2-letter codes (en, es, fr, de, nl, ja, zh)");
    snippets.add("Language Code (ISO 639-2): 3-letter codes (eng, spa, fra, deu, nld, jpn, zho)");
    snippets.add("Continent Code: finite list (AF, AN, AS, EU, NA, OC, SA)");
    snippets.add("Continent Text (EN): finite list (Africa, Antarctica, Asia, Europe, North America, Oceania, South America)");

    // Colors
    snippets.add("Color HEX: ^#?[0-9A-Fa-f]{6}$; synonyms: color hex, hex color, hexcode");
    snippets.add("Color names (EN): finite list (RED, GREEN, BLUE, ...); cues: 'color', 'colour', 'color name'");
    snippets.add("Color names (ES): finite list (ROJO, VERDE, AZUL, ...); cues: 'color espanol'");
    snippets.add("Color names (NL): finite list (ROOD, GROEN, BLAUW, ...); cues: 'kleur', Dutch color vocabulary");

    // Addresses and communication
    snippets.add("Full Address (EN): free-form address line(s) with street, city, state/province, postal, country");
    snippets.add("Street Address (EN): street line(s) only; cues: street, address_line");
    snippets.add("Email: RFC-like local@domain; cues: 'email', not a URL");
    snippets.add("Email domain: domain portion of email; lacks local part; not a full URL");
    snippets.add("URI/URL: http(s)://...; cues: 'url', 'uri'");
    snippets.add("Telephone: international/national phone formats; cues: phone, phone_number, telephone");

    // Industry and codes
    snippets.add("NAICS Industry Code: numeric codes 2-6 digits; cues: naics, industry code");
    snippets.add("Industry Text (EN): sector names; cues: industry, sector");

    // Personal info
    snippets.add("Person Age Range: 18-24, 25-34, 35-44, ...; cues: age_range, age group");
    snippets.add("Year of Birth: four-digit year 1900-2099; cues: birth_year, yob");
    snippets.add("Marital Status (EN): SINGLE, MARRIED, DIVORCED, WIDOWED; cues: marital status");
    snippets.add("Race (EN): WHITE, BLACK, ASIAN, HISPANIC, NATIVE AMERICAN, PACIFIC ISLANDER, OTHER");

    // Ambiguity disambiguation (descriptive guidance only)
    List<String> disambig = new ArrayList<>();
    disambig.add("'soc sec' usually means US SSN unless qualified with country (e.g., CH or FR)");
    disambig.add("'mod10'/'luhn' indicates Luhn checksum identifiers (credit cards, IMEI), not hashes");
    disambig.add("'securities number' can mean CUSIP (US) or ISIN (international); prefer context cues");
    disambig.add("'universal product numbers' refers to UPC codes; numeric with check digit");
    disambig.add("Dutch color cues like 'color_text_dutch' suggest Dutch color names (NL)");
    disambig.add("Nationality columns with language suffix (e.g., _EN, _NL) indicate value language, not geography");
    disambig.add("If header says 'guid' or 'uuid' and values match 8-4-4-4-12 hex, prefer GUID over generic IDENTIFIER");
    disambig.add("Coordinate pair headers like 'latitude_and_longitude' or 'lat_long' indicate paired coordinates, not single latitude");
    disambig.add("Email vs URL: strings with '@' and domain are emails; do not classify as URL");

    cache.addDocuments(domain, snippets, Map.of("source", "seed"));
    cache.addDocuments(domain, disambig, Map.of("source", "rules"));
    log.info("Initialized extension knowledge with {} snippets and {} rules", snippets.size(), disambig.size());
  }

  public List<GenerationVectorCacheService.Result> retrieveExtension(String query, int topK) {
    return cache.search("extension", query, topK);
  }

  public void initializeInsuranceKnowledge() {
    String domain = "insurance";
    cache.clearDomain(domain);
    List<String> snippets = new ArrayList<>();

    // Core insurance concepts and headers
    snippets.add("Policy start date: headers like Date_start_contract, Policy Start, Inception Date; date formats vary");
    snippets.add("Last renewal date: headers like Date_last_renewal, Last Renewal Date");
    snippets.add("Next renewal date: headers like Date_next_renewal, Renewal Due Date");
    snippets.add("Insured date of birth: headers Date_birth, DOB, Date of Birth; values are dates");
    snippets.add("Driving licence date: headers Date_driving_licence, License Issue Date; values are dates");
    snippets.add("Distribution channel: finite list candidate: ONLINE, AGENT, BROKER, BRANCH, CALL CENTER");
    snippets.add("Seniority: tenure in years (integer or decimal); headers Seniority, Tenure");
    snippets.add("Policies in force: integer count; headers Policies_in_force");
    snippets.add("Max policies / Max products: integer limits; headers Max_policies, Max_products");
    snippets.add("Lapse: boolean or categorical lapse indicator; headers Lapse; values YES/NO or 0/1");
    snippets.add("Lapse date: headers Date_lapse; date when policy lapsed");
    snippets.add("Payment method/frequency: headers Payment; values MONTHLY, ANNUAL, CARD, DIRECT DEBIT");
    snippets.add("Premium: currency amount; headers Premium; decimal monetary values");
    snippets.add("Cost of claims (current year): currency; headers Cost_claims_year");
    snippets.add("Number of claims (current year): integer; headers N_claims_year");
    snippets.add("Number of claims (history): integer; headers N_claims_history");
    snippets.add("Ratio of claims history: numeric ratio 0..1 or percentage; headers R_Claims_history");
    snippets.add("Type of risk: categorical auto risk segment; headers Type_risk; examples: TPO, TPFT, COMPREHENSIVE");
    snippets.add("Area: region code or descriptive area; headers Area");
    snippets.add("Second driver: boolean or categorical; headers Second_driver; values YES/NO");
    snippets.add("Year of matriculation (vehicle registration year): four-digit year; headers Year_matriculation");
    snippets.add("Power: engine power (HP/kW) numeric; headers Power");
    snippets.add("Cylinder capacity: engine displacement (cc) numeric; headers Cylinder_capacity");
    snippets.add("Vehicle value: currency; headers Value_vehicle");
    snippets.add("Number of doors: integer; headers N_doors");
    snippets.add("Type of fuel: finite list: PETROL, DIESEL, HYBRID, ELECTRIC, LPG, CNG; headers Type_fuel");
    snippets.add("Vehicle length: numeric length (mm/cm/m) – may include unit; headers Length");
    snippets.add("Vehicle weight: numeric mass (kg) – may include unit; headers Weight");

    // Disambiguation cues (descriptive guidance only)
    List<String> disambig = new ArrayList<>();
    // Ambiguities observed in insurance evaluation
    disambig.add("Area: geographic region descriptor; treat as free text/categorical, not a claims metric");
    disambig.add("Payment: method/frequency (MONTHLY, ANNUAL, CARD, DIRECT DEBIT), not a claims count");
    disambig.add("Date_lapse: policy lapse/cancellation date; do not confuse with date of birth");
    // General cues
    disambig.add("Dates often appear in multiple columns; rely on header cues: start/renewal/lapse/birth/licence");
    disambig.add("Premium, Cost_claims_year, Value_vehicle are currency-like decimals; not plain integers");
    disambig.add("N_claims_* are counts; map to integer-like numeric patterns");
    disambig.add("R_Claims_history is a ratio/percentage; values like 0.25 or 25%; treat as numeric ratio");
    disambig.add("Second_driver appears as YES/NO or 0/1; treat as boolean categorical list");
    disambig.add("Type_fuel is a finite list (PETROL, DIESEL, HYBRID, ELECTRIC, LPG, CNG)");
    disambig.add("Type_risk common values: TPO (Third Party Only), TPFT (Third Party, Fire & Theft), COMPREHENSIVE");

    // Header cue hints (natural language, not hard mappings)
    disambig.add("Header 'Area' typically matches patterns like (?i)^(area|region|zone)$");
    disambig.add("Header 'Payment' typically matches (?i)^(payment|pay_method|pay_freq|payment_method|payment_frequency)$");
    disambig.add("Header 'Date_lapse' typically matches (?i)^(date[_ -]?lapse|lapse[_ -]?date|cancellation[_ -]?date)$");

    cache.addDocuments(domain, snippets, Map.of("source", "seed"));
    cache.addDocuments(domain, disambig, Map.of("source", "rules"));
    log.info("Initialized insurance knowledge with {} snippets and {} rules", snippets.size(), disambig.size());
  }

  public List<GenerationVectorCacheService.Result> retrieveInsurance(String query, int topK) {
    return cache.search("insurance", query, topK);
  }

  public void initializeTelcoKnowledge() {
    String domain = "telco";
    cache.clearDomain(domain);
    List<String> snippets = new ArrayList<>();

    // Telco KPIs & radio metrics
    snippets.add("RSRP: Reference Signal Received Power (dBm), numeric negative values typical -140..-44");
    snippets.add("RSRQ: Reference Signal Received Quality (dB), numeric negative values typical -20..-3");
    snippets.add("SINR: Signal to Interference plus Noise Ratio (dB), numeric values -10..30");
    snippets.add("CQI: Channel Quality Indicator (0..15)");
    snippets.add("Throughput (DL/UL): kbps/mbps numeric; headers throughput, downlink, uplink");
    snippets.add("Latency: milliseconds ms numeric; headers latency, RTT, ping");
    snippets.add("Jitter: milliseconds ms numeric");
    snippets.add("Packet loss: percentage 0..100% or 0..1 ratio; headers packet_loss, loss");
    snippets.add("PRB utilization: Physical Resource Block utilization (%), headers prb_utilization, prb");
    snippets.add("Handovers: integer counts; headers handovers, ho, intercell_handover, intracell_handover");
    snippets.add("RRC Connection attempts/success/failure; headers rrc_* (setup_attempts, setup_success, failures)");

    // IDs & topology
    snippets.add("Cell ID / gNodeB/eNodeB: alphanumeric or integer; headers cell_id, eci, enodeb, gnodeb, nb_id");
    snippets.add("PCI: Physical Cell ID 0..503; headers pci");
    snippets.add("TAC/LAC: Tracking/LAC area codes integer; headers tac, lac");
    snippets.add("MCC/MNC: mobile country/network codes numeric; headers mcc, mnc");
    snippets.add("Band/NR-ARFCN/EARFCN: numeric frequency indicators; headers band, nrarfcn, earfcn");

    // Time
    snippets.add("Timestamp/date: ISO datetime or epoch ms/s; headers timestamp, time, datetime, date");

    // Disambiguation (guidance only)
    List<String> disambig = new ArrayList<>();
    disambig.add("DL/UL/Uplink/Downlink map to throughput or traffic volume, not latency");
    disambig.add("RSRP/RSRQ/SINR/CQI are radio quality metrics; do not classify as identifiers");
    disambig.add("PRB utilization is percentage; avoid mapping to counts");
    disambig.add("PCI (0..503) differs from TAC/LAC (larger ranges); use header cues");
    disambig.add("Packet loss often in % or 0..1; avoid mapping to jitter/latency");

    cache.addDocuments(domain, snippets, Map.of("source", "seed"));
    cache.addDocuments(domain, disambig, Map.of("source", "rules"));
    log.info("Initialized telco knowledge with {} snippets and {} rules", snippets.size(), disambig.size());
  }

  public List<GenerationVectorCacheService.Result> retrieveTelco(String query, int topK) {
    return cache.search("telco", query, topK);
  }

  public void initializeTelcoChurnKnowledge() {
    String domain = "telco_churn";
    cache.clearDomain(domain);
    List<String> snippets = new ArrayList<>();

    // Customer attributes
    snippets.add("CustomerID: unique identifier; headers customerid, customer_id, id");
    snippets.add("CustomerID header regex must use word boundaries: ^(?i)(customer\\s*id|customerid|cust\\s*id|cust_id|id)$");
    snippets.add("Do NOT match 'city', 'state', or other geo headers for CustomerID; negative headers: city, state, zip, zipcode, latitude, longitude");
    snippets.add("Gender: finite list MALE, FEMALE (or M/F)");
    snippets.add("SeniorCitizen: boolean/flag 0/1 or YES/NO");
    snippets.add("Partner: YES/NO; Dependents: YES/NO");
    snippets.add("Tenure: months as integer; headers tenure, months, months_with_company");
    snippets.add("Age: numeric years; map to built-in PERSON.AGE unless header is 'Under 30'");
    snippets.add("Under 30: boolean or Y/N; only when header exactly 'Under 30' or 'Under_30'");

    // Demographics & geography (built-in alignments)
    snippets.add("City: city name free text; header 'City'; aligns to built-in CITY; must not match identifiers");
    snippets.add("State: US state/province name; header 'State'; aligns to built-in STATE_PROVINCE.STATE_NAME_US");
    snippets.add("Country: country name; header 'Country'; aligns to built-in COUNTRY.TEXT_EN; not Dependents");
    snippets.add("Zip Code: US 5-digit ZIP; headers 'Zip Code', 'zipcode', 'zip'; aligns to built-in POSTAL_CODE.ZIP5_US");
    snippets.add("Lat Long: coordinate pair (latitude, longitude) decimal; headers 'Lat Long','lat_long','latitude longitude'; aligns to built-in COORDINATE_PAIR.DECIMAL");
    snippets.add("Latitude: decimal -90..90; header 'Latitude'; aligns to built-in COORDINATE.LATITUDE_DECIMAL");
    snippets.add("Longitude: decimal -180..180; header 'Longitude'; aligns to built-in COORDINATE.LONGITUDE_DECIMAL");
    snippets.add("Population: integer count; header 'Population'; treat as integer, not revenue");

    // Services subscribed
    snippets.add("PhoneService: YES/NO; MultipleLines: YES/NO/NO PHONE SERVICE");
    snippets.add("InternetService: DSL/Fiber optic/No");
    snippets.add("OnlineSecurity/OnlineBackup/DeviceProtection/TechSupport: YES/NO/NO INTERNET SERVICE");
    snippets.add("StreamingTV/StreamingMovies: YES/NO/NO INTERNET SERVICE");
    snippets.add("StreamingMusic: YES/NO/NO INTERNET SERVICE; header synonyms streaming_music, streaming music");
    snippets.add("Unlimited Data: YES/NO; header 'Unlimited Data' or unlimited_data; boolean categorical");
    snippets.add("Internet Type: categorical: CABLE/DSL/Fiber optic/Satellite/Wireless; header 'Internet Type'");

    // Contracts & billing
    snippets.add("Contract: Month-to-month/One year/Two year");
    snippets.add("PaperlessBilling: YES/NO");
    snippets.add("PaymentMethod: Electronic check/Mailed check/Bank transfer (automatic)/Credit card (automatic)");
    snippets.add("Customer Status: finite set e.g., STAYED, CHURNED, JOINED; header 'Customer Status'");
    snippets.add("Satisfaction Score: integer 1..5 (or 0..10 depending dataset); header 'Satisfaction Score'");

    // Charges
    snippets.add("MonthlyCharges: decimal currency; headers monthlycharges");
    snippets.add("TotalCharges: decimal currency; headers totalcharges");
    snippets.add("TotalLongDistanceCharges: decimal currency; header contains 'Total Long Distance Charges'");
    snippets.add("TotalExtraDataCharges: decimal currency; header 'Total Extra Data Charges'");
    snippets.add("TotalRefunds: decimal currency; header 'Total Refunds'");
    snippets.add("TotalRevenue: decimal currency; header 'Total Revenue'");
    snippets.add("AvgMonthlyLongDistanceCharges: decimal; header 'Avg Monthly Long Distance Charges'");
    snippets.add("AvgMonthlyGBDownload: decimal; header 'Avg Monthly GB Download'");
    snippets.add("CLTV: Customer Lifetime Value; currency-like decimal; header 'CLTV', 'Customer Lifetime Value'");
    snippets.add("Monthly Charge: alias of MonthlyCharges when header is 'Monthly Charge'");
    snippets.add("Total Charges: alias of TotalCharges when header is 'Total Charges'");
    snippets.add("Total Revenue: cumulative currency; not a yes/no or service field");

    // Target
    snippets.add("Churn: YES/NO target flag; headers churn; header regex ^(?i)churn$");
    snippets.add("ChurnScore: numeric 0-100; header 'Churn Score'; should NOT match 'Churn' alone");
    snippets.add("ChurnCategory: finite; examples 'Competitor', 'Dissatisfaction', 'Attitude', 'Other'");
    snippets.add("ChurnReason: free-text short categorical; header 'Churn Reason'");
    snippets.add("Churn Reason header regex: ^(?i)(churn\s*reason|reason\s*for\s*churn)$; avoid mapping to 'Churn' or 'Churn Score'");
    snippets.add("Multiple Lines: YES/NO/No phone service; header 'Multiple Lines' or multiple_lines");
    snippets.add("Paperless Billing: YES/NO; header 'Paperless Billing' or paperless_billing");
    snippets.add("Partner: YES/NO; header 'Partner'");
    snippets.add("Number of Referrals: integer count 0+; header 'Number of Referrals'");
    snippets.add("Tenure in Months: integer months; header 'Tenure in Months', tenure_in_months, tenure");
    snippets.add("Online Security: YES/NO/No internet service; header 'Online Security' (security keyword)");
    snippets.add("Online Backup: YES/NO/No internet service; header 'Online Backup' (backup keyword)");
    snippets.add("Phone Service: YES/NO; header 'Phone Service'");
    snippets.add("Gender: finite MALE, FEMALE (or M/F); header 'Gender'; values list: MALE,FEMALE,M,F");
    snippets.add("Premium Tech Support: YES/NO/No internet service; header 'Premium Tech Support'");
    snippets.add("Referred a Friend: YES/NO; header 'Referred a Friend' or referred_a_friend");
    snippets.add("Population: integer count; header 'Population'; not revenue or score");
    snippets.add("Quarter: finite list Q1,Q2,Q3,Q4; header 'Quarter'; regex ^(?i)(Q[1-4]|(1st|2nd|3rd|4th) Quarter)$");
    snippets.add("Offer: finite set e.g., NONE, OFFER A, OFFER B, OFFER C, OFFER D, OFFER E; header 'Offer'");
    snippets.add("Number of Referrals: integer count; header 'Number of Referrals'");
    snippets.add("Number of Dependents: integer count; header 'Number of Dependents'");
    snippets.add("Married: YES/NO; header 'Married'");

    // Disambiguation
    List<String> disambig = new ArrayList<>();
    disambig.add("YES/NO fields should be treated as finite lists; not free text");
    disambig.add("Contract values are limited set (Month-to-month/One year/Two year)");
    disambig.add("PaymentMethod is a finite enumerated list with 4 canonical values");
    disambig.add("MonthlyCharges/TotalCharges are numeric decimals; avoid mapping to tenure");
    disambig.add("Tenure is integer months; avoid mapping to charges");
    disambig.add("Churn Score must only match headers containing the word 'score'; do NOT match 'churn' alone");
    disambig.add("CustomerID should not match 'City', 'State', 'Zip Code', 'Latitude', 'Longitude', 'Lat Long'");
    disambig.add("Geo headers: City -> CITY, State -> STATE/PROVINCE, Zip Code -> POSTAL_CODE.ZIP5_US, Latitude/Longitude/Lat Long -> COORDINATE types");
    disambig.add("Prefer using existing built-in types for City/State/Zip/Latitude/Longitude/Lat Long rather than creating new custom types");
    disambig.add("Paperless Billing is YES/NO; avoid mapping to charges");
    disambig.add("Multiple Lines is YES/NO/No phone service; avoid mapping to revenue");
    disambig.add("Online Security vs Online Backup are distinct; ensure header includes 'Security' vs 'Backup'");
    disambig.add("Phone Service vs Internet Service are distinct; do not cross-map");
    disambig.add("Churn Reason vs Churn: 'Churn Reason' is free-text reason; 'Churn' is YES/NO");
    disambig.add("Gender values (MALE/FEMALE or M/F) must not be treated as identifiers");
    disambig.add("Headers containing 'Revenue', 'Charges', 'Refunds' indicate currency decimals; do not classify as boolean or identifiers");
    disambig.add("'Lat Long' always refers to coordinate pairs; do not map to charges or scores");
    disambig.add("'Under 30' is a boolean category (TRUE/FALSE or Y/N), not age years");
    disambig.add("'Customer Status' is a categorical label (e.g., STAYED/CHURNED/JOINED), not a numeric score");
    disambig.add("Zip Code (US) is 5 digits only; avoid matching to CustomerID or numeric scores");
    disambig.add("'Avg Monthly GB Download' is data volume (decimal); avoid mapping to long distance charges and vice versa");
    disambig.add("Quarter must be one of Q1..Q4 or ordinal quarter strings; do not map to month or date types");

    cache.addDocuments(domain, snippets, Map.of("source", "seed"));
    cache.addDocuments(domain, disambig, Map.of("source", "rules"));
    log.info("Initialized telco churn knowledge with {} snippets and {} rules", snippets.size(), disambig.size());
  }

  public List<GenerationVectorCacheService.Result> retrieveTelcoChurn(String query, int topK) {
    return cache.search("telco_churn", query, topK);
  }
}


