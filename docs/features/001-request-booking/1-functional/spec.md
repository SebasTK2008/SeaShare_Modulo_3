# Feature Specification: UC01 - Request Booking Quote

**Created**: 2026-08-27

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Calculate bulk quotes for boat listing (Priority: P1)

As a Renter, when viewing the list of booking options in Booking Module, I want to see the estimated cost (quote) for each boat in the list, so that I can compare options based on price before proceeding with the booking.

**System Context (Flow)**: 
1. Booking Module (Bookings and Operations) loads the booking options.
2. Booking Module triggers this use case by sending a list of identifiers (`boat_ids`) along with the dates to Finance module.
3. Finance module invokes the sub-case "Provide base rate" by querying Fleet Management Module for each boat's rate.
4. Finance module performs the calculations and returns the quotes to Booking Module.

**Why this priority**: It is essential to show estimated prices from the initial search to capture the customer's attention and allow quick comparison of options, directly impacting conversion.

**Independent Test**: Send a simulated payload from Booking Module with a list of IDs to Finance module, validating that it returns the array of quotes after querying Fleet Management Module.

**Acceptance Scenarios**:
1. **Scenario**: Successful calculation of multiple quotes.
   - **Given** a list of 10 boats on the screen.
   - **When** Booking Module sends the list to Finance module.
   - **Then** Finance module successfully calculates and returns all 10 quotes.

---

### User Story 2 - Individual quote on the boat detail page (Priority: P1)

As a Renter, when entering the detail page of a specific boat, I want to view the exact quote for my trip dates, to evaluate if it fits my budget before starting the formal booking process.

**Why this priority**: It is the mandatory previous step before confirming any individual booking.

**Independent Test**: Verify that upon selecting dates in the detail view, Booking Module sends a single ID to Finance module and it returns the corresponding quote.

**Acceptance Scenarios**:
1. **Scenario**: Quote for a single boat
   - **Given** the user is on the detail view of a boat
   - **When** they select the start and end dates
   - **Then** Finance module calculates the value only for that boat and returns it to Booking Module to be displayed on screen.

---

### User Story 3 - Price transparency and warnings (Priority: P3)

As a Renter, when viewing any quote, I want to receive a very clear visual warning that the displayed price is an estimate based on the base rate and NOT the final value, to avoid surprises with possible security deposits or extra charges at the time of payment.

**Why this priority**: Critical business requirement to avoid friction and user complaints.

**Independent Test**: Check that all responses from Finance module include a "warning" flag or text and that the frontend (Booking Module) renders it obligatorily.

**Acceptance Scenarios**:
1. **Scenario**: Display of the warning
   - **Given** a successfully calculated quote
   - **When** it is shown in the user interface
   - **Then** a visible disclaimer must appear (e.g., info icon or small print text) indicating "Estimated value. Does not include additional charges or security deposit".

---

### User Story 4 - Delegation of calculations (Booking Module) (Priority: P2)

As Booking Module (Booking Management), I want to be able to send quote requests (bulk or individual) to Finance module, to fully delegate the responsibility of financial calculations and keep my own logic focused solely on booking availability and status.

**Why this priority**: Keeps the architecture clean, modular, and the separation of responsibilities between contexts.

**Independent Test**: Ensure that there are no rate multiplications in the Booking Module code, limiting it to being a client of the Finance module API.

**Acceptance Scenarios**:
1. **Scenario**: Booking Module requests quote delegating the calculations
   - **Given** Booking Module needs to present prices to the user
   - **When** it requests costs for one or several boats
   - **Then** it sends the request to Finance module and consumes the result without performing mathematical operations on the base rate locally.

---

### User Story 5 - Provision of base rate (Fleet Management Module) (Priority: P2)

As Fleet Management Module (Inventory and Rates), I want to provide the "base rate" per boat, so that Finance module can consume it in bulk without creating bottlenecks or degrading my performance.

**Why this priority**: Fleet Management Module is the source of truth for prices. If its response is slow, the entire flow of quotes from Finance module and display in Booking Module will be heavily affected.

**Independent Test**: Perform load tests requesting rates for 50 concurrent boats from Finance module to Fleet Management Module, expecting response times under 500ms.

**Acceptance Scenarios**:
1. **Scenario**: Fleet Management Module returns the base rate in optimal time
   - **Given** a request from Finance module to query the base rate of multiple boats
   - **When** Fleet Management Module processes the request
   - **Then** it correctly returns the base rate for the requested boats in under 500ms.

### Edge Cases

- What happens when Fleet Management Module is down, times out, or is unreachable when Finance module requests base rates?
- What happens when the dates requested by Booking Module include invalid formats, past dates, or end dates that occur before start dates?
- How does the system handle quotes for a boat that currently has no base rate configured (null or missing) in Fleet Management Module?
- What happens if the `boat_ids` list sent by Booking Module is empty or contains non-existent IDs?
- How is the system expected to handle unusually large payloads (e.g., requesting quotes for 1,000 boats at once)?
- How does the calculation behave if the requested booking duration is 0 days?


## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Finance module MUST return accurate quotes to Booking Module based on the requested list of boat identifiers.
- **FR-002**: The system MUST calculate bulk quotes using the established pricing formula: `(boat base rate * duration in days) + (insurance fee * number of passengers)`. *(Note: Default duration is 1 day; default passenger count is 1).*
- **FR-003**: The system MUST calculate individual quotes using the same pricing formula as bulk quotes, applied to a single boat.
- **FR-004**: Finance module MUST expose an endpoint to receive quote requests from Booking Module and process them successfully.
- **FR-005**: Finance module MUST query Fleet Management Module by sending a list of boat IDs to retrieve their respective base rates.

### Non-Functional Requirements

- **NFR-001**: The system MUST use DTOs (Data Transfer Objects) for inter-module communication, mapping only the essential data attributes from Fleet Management Module and Booking Module payloads.
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
- **SC-002**: [Architectural Compliance, "0 mathematical or calculation operations related to pricing exist in the Booking Module codebase, ensuring 100% of price rendering is a direct mapping from Finance module API responses"]
- **SC-003**: [Business / Transparency, "Customer support tickets and disputes related to "unexpected fees" or "security deposits" represent less than 2% of total bookings within the first 60 days of release"]
- **SC-004**: [System Resilience, "100% of simulated network failures (e.g., Fleet Management Module timeouts or unavailable rates) trigger graceful fallbacks in Booking Module without causing application crashes, infinite loading states, or blank screens for the user"]
