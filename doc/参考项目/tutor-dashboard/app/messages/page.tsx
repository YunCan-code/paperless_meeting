"use client"

import { DashboardLayout } from "@/components/dashboard-layout"
import { MessagesOverview } from "@/components/messages/messages-overview"
import { ConversationsList } from "@/components/messages/conversations-list"
import { ChatInterface } from "@/components/messages/chat-interface"

export default function MessagesPage() {
  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Messages Overview */}
        <MessagesOverview />

        {/* Messages Interface */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[600px]">
          <div className="lg:col-span-1">
            <ConversationsList />
          </div>
          <div className="lg:col-span-2">
            <ChatInterface />
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
