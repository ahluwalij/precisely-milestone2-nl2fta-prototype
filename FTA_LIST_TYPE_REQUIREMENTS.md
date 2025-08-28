# FTA List Type Detection Requirements - Complete Documentation

## Executive Summary
FTA (Fast Text Analyzer) has specific requirements for detecting list-based semantic types that are not immediately obvious from the documentation. This document comprehensively documents all findings from extensive testing and source code analysis to enable proper implementation of list type generation.

## Critical Discovery: Minimum Sample Requirements

### Default Behavior (from LogicalTypeFiniteSimple.java:114)
```java
int minSamples = outliers.isEmpty() ? 8 : 20;
```
- **Default minimum: 8 samples** (when no outliers are present)
- **With outliers: 20 samples** (when outliers are detected)

### Header Pattern Impact on Sample Requirements (lines 116-118)
```java
if (headerConfidence > 0) {
    minCardinality = 1;
    minSamples = headerConfidence < 99 ? 4 : 1;
    maxOutliers = headerConfidence < 90 ? Math.max(4, baseOutliers) : getSize() / 2;
    if (headerConfidence >= 99)
        threshold -= 1;
}
```

**Key Finding**: Header pattern matching dramatically reduces sample requirements:
- **headerConfidence >= 99**: minSamples = **1** (can detect with just 1 sample!)
- **headerConfidence > 0 and < 99**: minSamples = **4**
- **No header match (headerConfidence = 0)**: minSamples = **8** (default)

## Proven Test Results

### Test Case 1: AIRLINE.IATA_CODE
```json
{
  "headerRegExps": [{
    "regExp": ".*(?i)(iata|air).*",
    "confidence": 99,
    "mandatory": true
  }]
}
```
- Column named "iata": ✅ **Detected with 2 samples** (headerConfidence = 99)
- Column named "code": ❌ **NOT detected with 2 samples** (headerConfidence = 0)
- Column named "airline": ✅ **Detected with 2 samples** (headerConfidence = 99)

### Test Case 2: Custom TEST.WITH_HEADER
```json
{
  "headerRegExps": [{
    "regExp": "(?i).*mytest.*",
    "confidence": 99,
    "mandatory": false
  }]
}
```
- Column named "mytest_column": ✅ **Detected with 2 samples**
- Column named "random_column": ❌ **NOT detected with 2 samples**
- Column named "random_column": ❌ **NOT detected even with 8 samples** (other factors apply)

### Test Case 3: Custom TEST.UNIQUE_LIST (no header patterns)
- 2 samples: ❌ Not detected
- 4 samples: ❌ Not detected  
- 7 samples: ❌ Not detected
- 8 samples: ✅ **Detected** (meets default minimum)

## Complete List Type Requirements

### 1. Required Fields for FTA Plugin Registration

```json
{
  "semanticType": "SEMANTIC.TYPE_NAME",
  "description": "Human-readable description",
  "pluginType": "list",              // REQUIRED: Must be "list"
  "threshold": 95,                   // Percentage of matches required (default: 95)
  "baseType": "STRING",              // Usually STRING for list types
  "priority": 2000,                  // Higher = higher precedence (min: 2000 for external)
  "minSamples": -1,                  // -1 means use defaults (optional)
  "validLocales": [{
    "localeTag": "*",               // "*" for all locales, or specific like "en-US"
    "headerRegExps": [{             // CRITICAL for reducing sample requirements
      "regExp": "pattern",          
      "confidence": 99,             // 99 enables 1-sample detection!
      "mandatory": false            // true = must match to consider type
    }],
    "matchEntries": [{              // AUTO-GENERATED from content.members
      "regExpReturned": "(?i)(VAL1|VAL2|...)",
      "isRegExpComplete": true      // Optional, often omitted
    }]
  }],
  "content": {
    "type": "inline",               // "inline", "resource", or "file"
    "members": ["VALUE1", "VALUE2"]  // MUST use "members" not "values"!
  },
  "backout": "\\p{IsAlphabetic}+"   // CRITICAL: Pattern for non-list values
}
```

### 2. Critical Implementation Requirements

#### A. Value Case Requirements
- **ALL list values MUST be UPPERCASE** for English locales
- FTA explicitly checks and rejects lowercase values (LogicalTypeFiniteSimpleExternal.java:32)
- This applies to the `content.members` array

#### B. Content Structure
- Must use `"members"` as the key, NOT `"values"`
- FTA's Content.java expects: `public String[] members;`
- Values must be uppercase: `["RED", "GREEN", "BLUE"]` not `["red", "green", "blue"]`

#### C. Backout Pattern Requirements
- **CANNOT be generic ".*"** - this will cause detection to fail
- Must be specific to the expected pattern of the data
- Examples of good backout patterns:
  - `"\\p{IsAlphabetic}+"` - for single words
  - `"\\p{Alnum}{2}"` - for 2-character alphanumeric codes
  - `"[A-Z]{3}"` - for 3-letter uppercase codes
  - `"[ \\p{IsAlphabetic}]+"` - for phrases with spaces

#### D. Priority Considerations
- Built-in types typically have priorities 2000-3000
- Custom types should use higher priorities (5000+) to override built-ins
- When multiple types match, higher priority wins
- If priorities are equal, the type with more values typically wins

#### E. Minimum List Size Considerations
```java
int minCardinality = Math.min(getSize(), 4);
```
- Lists should have at least 4 unique values for reliable detection
- With header match: minCardinality = 1
- Very small lists (< 4 values) may not be detected without header patterns

### 3. Header Pattern Strategy

#### Confidence Levels and Their Impact:
- **confidence = 99**: Enables single-sample detection (minSamples = 1)
- **confidence = 95**: Requires 4 samples (minSamples = 4)
- **confidence = 90**: Requires 4 samples but allows more outliers
- **confidence < 90**: Still helps but with less impact

#### Mandatory Flag:
- `mandatory: true`: Type will ONLY be considered if header matches
- `mandatory: false`: Header match boosts confidence but isn't required
- Use `mandatory: true` when column name is critical for disambiguation

#### Pattern Design:
- Use `(?i)` for case-insensitive matching
- Use `.*` at start/end for substring matching
- Examples:
  - `"(?i).*pizza.*topping.*"` - matches "pizza_toppings", "PizzaToppingType"
  - `"(?i)(iata|airline|carrier)"` - matches any of these words
  - `"(?i)^account.*type$"` - exact start/end matching

### 4. Why Custom List Types Fail - Common Issues

1. **Insufficient Samples**
   - Without header match: Need 8+ samples
   - Test with only 2-3 rows will fail

2. **Generic Backout Pattern**
   - Using `".*"` makes FTA skip the type
   - Must use specific pattern like `"\\p{IsAlphabetic}+"`

3. **Missing or Wrong Content Structure**
   - Using `"values"` instead of `"members"`
   - Not uppercasing the values

4. **No Header Pattern Match**
   - Column name doesn't match any header pattern
   - Header confidence too low (< 90)

5. **Lower Priority Than Built-in Types**
   - Built-in type with same/similar values takes precedence
   - Solution: Use higher priority (10000+)

### 5. Optimization Strategies for List Type Generation

#### A. Always Include Header Patterns
```json
"headerRegExps": [{
  "regExp": "(?i).*(semantic|type|keywords).*",
  "confidence": 99,
  "mandatory": false
}]
```
This enables detection with minimal samples when column names match.

#### B. Generate Appropriate Backout Patterns
Based on the list content, generate specific patterns:
- All uppercase letters: `"[A-Z]+"`
- Mixed case single words: `"\\p{IsAlphabetic}+"`
- Phrases with spaces: `"[ \\p{IsAlphabetic}]+"`
- Alphanumeric codes: `"\\p{Alnum}{minLen,maxLen}"`
- With numbers: `"\\p{IsAlphabetic}+\\d*"`

#### C. Set High Priority for Custom Types
```json
"priority": 10000  // Ensures custom types override built-ins
```

#### D. Include Multiple Header Variations
```json
"headerRegExps": [
  {"regExp": "(?i).*account.*type.*", "confidence": 99, "mandatory": false},
  {"regExp": "(?i).*acct.*type.*", "confidence": 95, "mandatory": false},
  {"regExp": "(?i).*banking.*type.*", "confidence": 90, "mandatory": false}
]
```

### 6. Testing Recommendations

#### Minimum Test Dataset:
- **With good header match**: 2 samples sufficient
- **Without header match**: 8+ samples required
- **With outliers present**: 20+ samples needed

#### Test Column Names:
- Test with names that match header patterns
- Test with generic names to verify 8-sample detection
- Test with misleading names to ensure no false positives

### 7. Code References

Key FTA source files that control this behavior:
- `LogicalTypeFiniteSimple.java:107-135` - analyzeSet() method with sample requirements
- `LogicalTypeFiniteSimpleExternal.java:27-36` - Uppercase validation
- `PluginLocaleEntry.java` - getHeaderConfidence() implementation
- `PluginDefinition.java` - Plugin structure definition
- `Content.java` - Content structure with members field

### 8. Example: Properly Configured List Type

```json
{
  "semanticType": "PRODUCT.CATEGORY",
  "description": "E-commerce product categories",
  "pluginType": "list",
  "threshold": 95,
  "baseType": "STRING",
  "priority": 10000,
  "minSamples": -1,
  "validLocales": [{
    "localeTag": "*",
    "headerRegExps": [
      {"regExp": "(?i).*(category|categories).*", "confidence": 99, "mandatory": false},
      {"regExp": "(?i).*(product.*type|item.*type).*", "confidence": 95, "mandatory": false},
      {"regExp": "(?i).*cat.*", "confidence": 90, "mandatory": false}
    ]
  }],
  "content": {
    "type": "inline",
    "members": ["ELECTRONICS", "CLOTHING", "BOOKS", "HOME", "SPORTS", "TOYS"]
  },
  "backout": "[A-Z][A-Z& ]+"
}
```

This configuration will:
- Detect with 1 sample if column is named "category" (99 confidence)
- Detect with 4 samples if column is named "product_type" (95 confidence)  
- Detect with 8 samples if column has unrelated name
- Override built-in types due to high priority (10000)
- Match uppercase words potentially with spaces/ampersands

## Conclusion

The key to successful FTA list type detection is understanding the interplay between:
1. **Sample count requirements** (1, 4, or 8 based on header confidence)
2. **Header pattern matching** (reduces sample requirements dramatically)
3. **Proper backout patterns** (must be specific, not generic)
4. **Correct structure** (uppercase values, "members" key)

With proper configuration, list types can be detected with as few as 1-2 samples, making them highly effective for real-world data analysis scenarios.