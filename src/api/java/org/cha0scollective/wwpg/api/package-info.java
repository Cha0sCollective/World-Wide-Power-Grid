/**
 * Stationary electrical extension API 1.0.
 *
 * <p>All quantities use SI units. Element voltage is positive-node voltage minus
 * negative-node voltage; positive current flows from the positive node to the
 * negative node. Positive power is absorbed by the element. A supplying source
 * therefore normally has negative current and power. Native terminal numbers
 * are explicit persistent IDs, never positions in a list.</p>
 *
 * <p>WWPG invokes preparation and gameplay commit once per server game tick.
 * Descriptions capture immutable inputs. Queries and preparation do not advance
 * stored energy. Only accepted electrical substeps advance solver history;
 * nonlinear trial iterations do not. The owning add-on persists committed state
 * received in results and supplies it when an element is reconstructed.</p>
 */
package org.cha0scollective.wwpg.api;
