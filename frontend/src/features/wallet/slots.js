import { createElement } from "react";
import { WalletBalanceBadge } from "./WalletBalanceBadge";

export default [
  {
    id: "wallet-balance-action",
    name: "header.actions",
    order: 4,
    element: createElement(WalletBalanceBadge),
  },
];
