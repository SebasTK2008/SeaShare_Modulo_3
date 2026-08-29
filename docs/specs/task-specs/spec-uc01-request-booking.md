# Feature Specification: UC01 - Request Booking Quote

**Created**: 2026-08-27

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Calculate bulk quotes for boat listing (Priority: P1)

As a Renter, when viewing the list of booking options in Module 2, I want to see the estimated cost (quote) for each boat in the list, so that I can compare options based on price before proceeding with the booking.

**System Context (Flow)**: 
1. Module 2 (Bookings and Operations) loads the booking options.
2. Module 2 triggers this use case by sending a list of identifiers (`boat_ids`) along with the dates to Module 3.
3. Module 3 invokes the sub-case "Provide base rate" by querying Module 1 for each boat's rate.
4. Module 3 performs the calculations and returns the quotes to Module 2.

**Why this priority**: It is essential to show estimated prices from the initial search to capture the customer's attention and allow quick comparison of options, directly impacting conversion.

**Independent Test**: Send a simulated payload from Module 2 with a list of IDs to Module 3, validating that it returns the array of quotes after querying Module 1.

**Acceptance Scenarios**:
1. **Scenario**: Successful calculation of multiple quotes.
   - **Given** a list of 10 boats on the screen.
   - **When** Module 2 sends the list to Module 3.
   - **Then** Module 3 successfully calculates and returns all 10 quotes.

---

### User Story 2 - Individual quote on the boat detail page (Priority: P1)

As a Renter, when entering the detail page of a specific boat, I want to view the exact quote for my trip dates, to evaluate if it fits my budget before starting the formal booking process.

**Why this priority**: It is the mandatory previous step before confirming any individual booking.

**Independent Test**: Verify that upon selecting dates in the detail view, Module 2 sends a single ID to Module 3 and it returns the corresponding quote.

**Acceptance Scenarios**:
1. **Scenario**: Quote for a single boat
   - **Given** the user is on the detail view of a boat
   - **When** they select the start and end dates
   - **Then** Module 3 calculates the value only for that boat and returns it to Module 2 to be displayed on screen.

---

### User Story 3 - Price transparency and warnings (Priority: P3)

As a Renter, when viewing any quote, I want to receive a very clear visual warning that the displayed price is an estimate based on the base rate and NOT the final value, to avoid surprises with possible security deposits or extra charges at the time of payment.

**Why this priority**: Critical business requirement to avoid friction and user complaints.

**Independent Test**: Check that all responses from Module 3 include a "warning" flag or text and that the frontend (Module 2) renders it obligatorily.

**Acceptance Scenarios**:
1. **Scenario**: Display of the warning
   - **Given** a successfully calculated quote
   - **When** it is shown in the user interface
   - **Then** a visible disclaimer must appear (e.g., info icon or small print text) indicating "Estimated value. Does not include additional charges or security deposit".

---

### User Story 4 - Delegation of calculations (Module 2) (Priority: P2)

As Module 2 (Booking Management), I want to be able to send quote requests (bulk or individual) to Module 3, to fully delegate the responsibility of financial calculations and keep my own logic focused solely on booking availability and status.

**Why this priority**: Keeps the architecture clean, modular, and the separation of responsibilities between contexts.

**Independent Test**: Ensure that there are no rate multiplications in the Module 2 code, limiting it to being a client of the Module 3 API.

**Acceptance Scenarios**:
1. **Scenario**: Module 2 requests quote delegating the calculations
   - **Given** Module 2 needs to present prices to the user
   - **When** it requests costs for one or several boats
   - **Then** it sends the request to Module 3 and consumes the result without performing mathematical operations on the base rate locally.

---

### User Story 5 - Provision of base rate (Module 1) (Priority: P2)

As Module 1 (Inventory and Rates), I want to provide the "base rate" per boat, so that Module 3 can consume it in bulk without creating bottlenecks or degrading my performance.

**Why this priority**: Module 1 is the source of truth for prices. If its response is slow, the entire flow of quotes from Module 3 and display in Module 2 will be heavily affected.

**Independent Test**: Perform load tests requesting rates for 50 concurrent boats from Module 3 to Module 1, expecting response times under 500ms.

**Acceptance Scenarios**:
1. **Scenario**: Module 1 returns the base rate in optimal time
   - **Given** a request from Module 3 to query the base rate of multiple boats
   - **When** Module 1 processes the request
   - **Then** it correctly returns the base rate for the requested boats in under 500ms.

### Edge Cases

- What happens when Module 1 is down, times out, or is unreachable when Module 3 requests base rates?
- What happens when the dates requested by Module 2 include invalid formats, past dates, or end dates that occur before start dates?
- How does the system handle quotes for a boat that currently has no base rate configured (null or missing) in Module 1?
- What happens if the `boat_ids` list sent by Module 2 is empty or contains non-existent IDs?
- How is the system expected to handle unusually large payloads (e.g., requesting quotes for 1,000 boats at once)?
- How does the calculation behave if the requested booking duration is 0 days?


## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Module 3 MUST return accurate quotes to Module 2 based on the requested list of boat identifiers.
- **FR-002**: The system MUST calculate bulk quotes using the established pricing formula: `(boat base rate * duration in days) + (insurance fee * number of passengers)`. *(Note: Default duration is 1 day; default passenger count is 1).*
- **FR-003**: The system MUST calculate individual quotes using the same pricing formula as bulk quotes, applied to a single boat.
- **FR-004**: Module 3 MUST expose an endpoint to receive quote requests from Module 2 and process them successfully.
- **FR-005**: Module 3 MUST query Module 1 by sending a list of boat IDs to retrieve their respective base rates.

### Non-Functional Requirements

- **NFR-001**: The system MUST use DTOs (Data Transfer Objects) for inter-module communication, mapping only the essential data attributes from Module 1 and Module 2 payloads.
- **NFR-002**: The system MUST use `BigDecimal` for all monetary calculations to guarantee precision and avoid rounding errors.
- **NFR-003**: The system MUST implement robust error handling (e.g., timeouts, fallbacks) to gracefully manage API communication failures between Modules 1, 2, and 3.

### Key Entities *(include if feature involves data)*

- **[Boat]**: Represents a physical watercraft available for booking. For this specific use case, its key attributes are a unique identifier (`id` of type Long) and a pricing rate (`baseRate` of type BigDecimal).

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: [Financial accuracy, "100% calculation precision with zero (0) penny/rounding errors detected across 100 automated transaction tests, validating the correct implementation of BigDecimal and the pricing formula"]
- **SC-002**: [Architectural Compliance, "0 mathematical or calculation operations related to pricing exist in the Module 2 codebase, ensuring 100% of price rendering is a direct mapping from Module 3 API responses"]
- **SC-003**: [Business / Transparency, "Customer support tickets and disputes related to "unexpected fees" or "security deposits" represent less than 2% of total bookings within the first 60 days of release"]
- **SC-004**: [System Resilience, "100% of simulated network failures (e.g., Module 1 timeouts or unavailable rates) trigger graceful fallbacks in Module 2 without causing application crashes, infinite loading states, or blank screens for the user"]
