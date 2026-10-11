# Cinematic interface

Sloplex uses a consistent dark cinema theme across phones, tablets, foldables,
and Android TV. Shared Compose tokens live in `:ui:design`: near-black canvas,
charcoal surfaces, warm amber actions, soft white text, and muted secondary text.
The play-mark launcher artwork matches the in-app brand.

Touch layouts use a bottom navigation bar below 600 dp and a navigation rail
at 600 dp and above. Connection and sign-in forms are centered and constrained
on larger displays. Home combines a featured backdrop, library shortcuts,
landscape continue-watching cards, and poster shelves. Libraries and search use
adaptive grids; details present metadata, playback actions, versions, and children
in a scrollable layout. Empty states explain what to do next.

TV uses a scrollable sidebar, larger text and spacing, and native TV Material
controls. Amber button focus and poster outlines make remote navigation visible.
Continue-watching cards show source-time progress and remaining minutes. Existing
row focus restoration and playback key handling remain in place.

Artwork requests retain the account-scoped cache keys and authenticated image
loader. Missing or failed artwork keeps a themed placeholder visible. Password
handling, server validation, and the Compose player binding follow the existing
session and playback implementations.
