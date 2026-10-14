# Feature: Depot, Inbox, Stash & Mailbox Systems

## 1. Explanation
Manages player private item storage: Town Depots (shared or town-specific), Depot Boxes, Character Inbox, Supply Stash, and Mailbox delivery (parcels and letters delivered to character town depots).

## 2. Impact
Critical inventory and logistical subsystem. Prevents item duplication and loss across towns and character mail deliveries.

## 3. Functionality Affected
- **Depot Boxes & Lockers**: Access to town depot chest and character depot items.
- **Supply Stash**: Stackable item storage window directly accessible from depot tiles or hotkeys.
- **Mail Delivery**: Mailbox item consumption, destination character/town lookup, parcel routing.

## 4. Agent Implementation Details
- Implement `DepotLocker`, `DepotChest`, and `SupplyStash` in `org.opentibia.canary.items.containers.depot`.
- Implement `MailService` parsing parcel labels, verifying recipient existence, and depositing into character inboxes.
- Ensure recursive serialization into player persistence.

## 5. Test Reference
- `org.opentibia.canary.items.containers.depot.DepotMailTest`: Tests sending a parcel through a mailbox and receiving it in the recipient's town depot inbox.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
