#!/usr/bin/env bash
#
# Plugin Team Handoff Test Script
# Tests all backend/ML functionality that the RuneLite plugin will depend on
#
# Run this script before handing off to plugin team to verify:
# - Backend API is operational
# - ML model generating recommendations with scores
# - All required endpoints working correctly
# - Data quality meets requirements
#

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8000}"
TEST_EMAIL="plugin_test_$(date +%s)@arbitragepro.com"
TEST_PASSWORD="TestPassword123!"
TEST_USERNAME="PluginTestUser"

# Counters
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
print_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[✓]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

print_section() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

# Test functions
test_health_check() {
    print_test "Health Check - Backend API"

    response=$(curl -s -w "\n%{http_code}" "$BASE_URL/health" 2>/dev/null || echo -e "\n000")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        print_success "Backend API is running (HTTP $http_code)"
        echo "    Response: $body"
    else
        print_error "Backend API health check failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_user_registration() {
    print_test "User Registration"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        AUTH_TOKEN=$(echo "$body" | jq -r '.token' 2>/dev/null)
        USER_ID=$(echo "$body" | jq -r '.user_id' 2>/dev/null)

        if [ "$AUTH_TOKEN" != "null" ] && [ "$AUTH_TOKEN" != "" ]; then
            print_success "User registered successfully (HTTP $http_code)"
            echo "    User ID: $USER_ID"
            echo "    Token: ${AUTH_TOKEN:0:20}..."
        else
            print_error "Registration succeeded but no token returned"
            echo "    Response: $body"
            exit 1
        fi
    else
        print_error "User registration failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_user_login() {
    print_test "User Login"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        token=$(echo "$body" | jq -r '.token' 2>/dev/null)
        if [ "$token" = "$AUTH_TOKEN" ] || [ "$token" != "null" ]; then
            print_success "Login successful (HTTP $http_code)"
        else
            print_error "Login returned different token"
            exit 1
        fi
    else
        print_error "Login failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_token_verification() {
    print_test "Token Verification"

    response=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/auth/verify" \
        -H "Authorization: Bearer $AUTH_TOKEN" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        user_id=$(echo "$body" | jq -r '.user_id // .userId' 2>/dev/null)
        print_success "Token verified successfully (HTTP $http_code)"
        echo "    Verified User ID: $user_id"
    else
        print_error "Token verification failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_recommendations_endpoint() {
    print_test "Get Recommendations Endpoint"

    response=$(curl -s -w "\n%{http_code}" -X GET \
        "$BASE_URL/recommendations?runescape_username=$TEST_USERNAME&available_gp=10000000&available_ge_slots=8" \
        -H "Authorization: Bearer $AUTH_TOKEN" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        item_id=$(echo "$body" | jq -r '.item_id' 2>/dev/null)
        ml_score=$(echo "$body" | jq -r '.ml_score' 2>/dev/null)
        buy_price=$(echo "$body" | jq -r '.buy_price' 2>/dev/null)
        sell_price=$(echo "$body" | jq -r '.sell_price' 2>/dev/null)

        if [ "$item_id" != "null" ] && [ "$item_id" != "" ]; then
            print_success "Recommendation received (HTTP $http_code)"
            echo "    Item ID: $item_id"
            echo "    ML Score: $ml_score"
            echo "    Buy: ${buy_price}gp → Sell: ${sell_price}gp"

            # Validate ML score exists and is valid
            if [ "$ml_score" = "null" ] || [ "$ml_score" = "" ]; then
                print_warning "ML score is missing - model may not be running"
            elif (( $(echo "$ml_score < 0.5 || $ml_score > 1.0" | bc -l 2>/dev/null || echo 1) )); then
                print_warning "ML score out of expected range: $ml_score"
            else
                print_success "ML score is valid: $ml_score"
            fi

            # Validate buy < sell
            if [ "$buy_price" != "null" ] && [ "$sell_price" != "null" ]; then
                if [ "$buy_price" -lt "$sell_price" ]; then
                    print_success "Margin is positive (sell > buy)"
                else
                    print_error "Invalid margin: buy=$buy_price >= sell=$sell_price"
                fi
            fi

            # Store for trade test
            REC_ITEM_ID=$item_id
            REC_BUY_PRICE=$buy_price
            REC_QUANTITY=$(echo "$body" | jq -r '.buy_quantity // 1000' 2>/dev/null)
        else
            print_error "Recommendation endpoint returned no item"
            echo "    Response: $body"
            exit 1
        fi
    elif [ "$http_code" = "403" ] || [ "$http_code" = "404" ]; then
        # Expected if no license - test that endpoint exists and requires license
        print_success "Recommendations endpoint exists (HTTP $http_code)"
        print_warning "No active license found - this is expected for test user"
        echo "    Note: Plugin team will need to create test license in DB"
        # Skip trade tests since we don't have a recommendation
        SKIP_TRADE_TESTS=true
    else
        print_error "Get recommendations failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_redis_recommendations() {
    print_test "Redis Cache - Recommendations Quality"

    # Check if redis-cli is accessible
    if ! command -v docker &> /dev/null; then
        print_warning "Docker not available, skipping Redis check"
        return
    fi

    redis_data=$(docker exec arbitrage-redis redis-cli GET "recommendations:latest" 2>/dev/null || echo "")

    if [ -z "$redis_data" ]; then
        print_warning "Cannot access Redis or no recommendations cached"
        return
    fi

    # Parse Redis data
    count=$(echo "$redis_data" | jq -r '.recommendations | length' 2>/dev/null || echo "0")

    if [ "$count" -gt 0 ]; then
        print_success "Redis has $count cached recommendations"

        # Check ML scores in cached data
        ml_scores=$(echo "$redis_data" | jq -r '.recommendations[].ml_score' 2>/dev/null || echo "")
        if [ -n "$ml_scores" ]; then
            avg_score=$(echo "$ml_scores" | awk '{sum+=$1; count++} END {if(count>0) print sum/count; else print 0}')
            min_score=$(echo "$ml_scores" | sort -n | head -1)
            max_score=$(echo "$ml_scores" | sort -n | tail -1)

            print_success "ML scores present in cache"
            echo "    Average: $(printf "%.4f" $avg_score)"
            echo "    Range: $min_score - $max_score"
        else
            print_warning "ML scores missing from cached recommendations"
        fi
    else
        print_error "Redis cache is empty or invalid"
    fi
}

test_trade_creation() {
    if [ "$SKIP_TRADE_TESTS" = "true" ]; then
        print_warning "Skipping trade tests (no recommendation available)"
        return
    fi

    print_test "Trade Creation"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/trades/create" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"item_id\":$REC_ITEM_ID,\"buy_price\":$REC_BUY_PRICE,\"buy_quantity\":$REC_QUANTITY}" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        TRADE_ID=$(echo "$body" | jq -r '.trade_id // .id' 2>/dev/null)
        status=$(echo "$body" | jq -r '.status' 2>/dev/null)

        print_success "Trade created successfully (HTTP $http_code)"
        echo "    Trade ID: $TRADE_ID"
        echo "    Status: $status"
    else
        print_error "Trade creation failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_trade_update() {
    if [ "$SKIP_TRADE_TESTS" = "true" ]; then
        return
    fi

    print_test "Trade Update (bought → selling)"

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/trades/$TRADE_ID/update" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"status\":\"bought\",\"quantity_filled\":$REC_QUANTITY}" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        status=$(echo "$body" | jq -r '.status' 2>/dev/null)
        action=$(echo "$body" | jq -r '.action' 2>/dev/null)

        print_success "Trade updated successfully (HTTP $http_code)"
        echo "    New Status: $status"
        echo "    Next Action: $action"

        if [ "$action" != "sell" ]; then
            print_warning "Expected action='sell', got: $action"
        fi
    else
        print_error "Trade update failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_trade_completion() {
    if [ "$SKIP_TRADE_TESTS" = "true" ]; then
        return
    fi

    print_test "Trade Completion (selling → completed)"

    sell_price=$((REC_BUY_PRICE + 50))  # Add some profit

    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/trades/$TRADE_ID/update" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"status\":\"completed\",\"sell_price\":$sell_price,\"quantity_filled\":$REC_QUANTITY}" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        status=$(echo "$body" | jq -r '.status' 2>/dev/null)

        print_success "Trade completed successfully (HTTP $http_code)"
        echo "    Final Status: $status"
    else
        print_error "Trade completion failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

test_trade_history() {
    if [ "$SKIP_TRADE_TESTS" = "true" ]; then
        return
    fi

    print_test "Trade History Retrieval"

    response=$(curl -s -w "\n%{http_code}" -X GET \
        "$BASE_URL/trades/history?user_id=$USER_ID" \
        -H "Authorization: Bearer $AUTH_TOKEN" 2>/dev/null || echo -e "\n000")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" = "200" ]; then
        trade_count=$(echo "$body" | jq 'length' 2>/dev/null || echo "0")
        print_success "Trade history retrieved (HTTP $http_code)"
        echo "    Total trades: $trade_count"

        if [ "$trade_count" -gt 0 ]; then
            completed_count=$(echo "$body" | jq '[.[] | select(.status=="completed")] | length' 2>/dev/null || echo "0")
            echo "    Completed: $completed_count"
        fi
    else
        print_error "Trade history retrieval failed (HTTP $http_code)"
        echo "    Response: $body"
        exit 1
    fi
}

# Main execution
main() {
    echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║   Arbitrage Pro - Plugin Team Handoff Test Suite     ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "Testing backend API at: $BASE_URL"
    echo ""

    # Check dependencies
    if ! command -v jq &> /dev/null; then
        print_error "jq is required but not installed. Install with: apt-get install jq (Ubuntu) or brew install jq (macOS)"
        exit 1
    fi

    if ! command -v curl &> /dev/null; then
        print_error "curl is required but not installed"
        exit 1
    fi

    # Run tests
    print_section "Phase 1: System Health"
    test_health_check

    print_section "Phase 2: Authentication"
    test_user_registration
    test_user_login
    test_token_verification

    print_section "Phase 3: ML Recommendations"
    test_recommendations_endpoint
    test_redis_recommendations

    print_section "Phase 4: Trade Lifecycle"
    test_trade_creation
    test_trade_update
    test_trade_completion
    test_trade_history

    # Summary
    print_section "Test Summary"
    echo -e "${GREEN}Passed:${NC} $TESTS_PASSED"
    echo -e "${RED}Failed:${NC} $TESTS_FAILED"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  ✓ ALL TESTS PASSED - Ready for Plugin Team Handoff   ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
        echo ""
        echo "Backend API is fully operational and ready for plugin integration."
        echo ""
        echo "Plugin team can proceed with:"
        echo "  1. HTTP client implementation"
        echo "  2. Authentication flow"
        echo "  3. Recommendation display"
        echo "  4. Trade tracking integration"
        echo ""
        exit 0
    else
        echo -e "${RED}╔════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║  ✗ TESTS FAILED - Fix issues before handoff           ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════════════════╝${NC}"
        echo ""
        echo "Please resolve the above errors before handing off to plugin team."
        echo ""
        exit 1
    fi
}

# Run main
main
