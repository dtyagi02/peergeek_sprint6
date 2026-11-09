import { createElement } from "react";
import { DomainChatPage } from "./DomainChatPage";
import { GlobalCommunityPage } from "./GlobalCommunityPage";

export default [
  {
    path: "/domains/:slug/community",
    element: createElement(DomainChatPage),
    visibility: "public",
  },
  {
    path: "/community",
    element: createElement(GlobalCommunityPage),
    visibility: "protected",
  },
];

