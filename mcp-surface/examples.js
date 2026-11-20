// Example tools/list responses for the playground. Each is what a server would
// send back from `tools/list`, so pasting a real server's response works the
// same way.

export const EXAMPLES = {
  broken: {
    label: "Broken server",
    server: "weather-mcp",
    tools: {
      tools: [
        {
          name: "get_forecast",
          description: "Daily forecast for a city.",
          inputSchema: {
            type: "object",
            properties: {
              location: { type: "string", description: "City name, e.g. Windsor, ON" },
              days: { type: "integer", minimum: "one" },
            },
            required: ["city"],
            additionalProperties: false,
          },
          annotations: { readOnlyHint: true },
        },
        {
          name: "search",
          description: "Search places by name.",
          inputSchema: { type: "object", properties: { q: { type: "string" } } },
        },
        {
          name: "search",
          description: "Search weather stations.",
          inputSchema: { type: "object", properties: { q: { type: "string" } } },
        },
        {
          name: "Get Alerts",
          inputSchema: { type: "object", properties: {} },
        },
        {
          name: "delete_alert_subscription",
          description: "Removes the subscription immediately.",
          inputSchema: {
            type: "object",
            properties: { id: { type: "string", description: "Subscription id" } },
            required: ["id"],
          },
        },
        {
          name: "list_stations",
          description: "Weather stations near a point.",
          inputSchema: { type: "array", items: { type: "number" } },
        },
      ],
    },
  },

  healthy: {
    label: "Healthy server",
    server: "notes-mcp",
    tools: {
      tools: [
        {
          name: "list_notes",
          description: "List notes, newest first.",
          inputSchema: {
            type: "object",
            properties: { limit: { type: "integer", minimum: 1, maximum: 100, description: "How many notes to return (default 20)" } },
          },
          annotations: { readOnlyHint: true },
        },
        {
          name: "get_note",
          description: "Fetch one note by id.",
          inputSchema: {
            type: "object",
            properties: { id: { type: "string", description: "Note id from list_notes" } },
            required: ["id"],
          },
          annotations: { readOnlyHint: true },
        },
        {
          name: "create_note",
          description: "Create a note and return its id.",
          inputSchema: {
            type: "object",
            properties: {
              title: { type: "string", description: "Short title" },
              body: { type: "string", description: "Markdown body" },
            },
            required: ["title"],
          },
          annotations: { readOnlyHint: false, destructiveHint: false },
        },
        {
          name: "delete_note",
          description: "Delete a note. This cannot be undone.",
          inputSchema: {
            type: "object",
            properties: { id: { type: "string", description: "Note id" } },
            required: ["id"],
          },
          annotations: { readOnlyHint: false, destructiveHint: true },
        },
      ],
    },
  },

  modern: {
    label: "JSON Schema 2020-12 (valid example)",
    server: "modern-example-mcp",
    tools: {
      tools: [
        {
          name: "search_pages",
          description: "Search archived pages, returning matching results.",
          inputSchema: {
            $schema: "https://json-schema.org/draft/2020-12/schema",
            type: "object",
            properties: {
              query: { type: "string", description: "Search text." },
              options: {
                type: "array",
                prefixItems: [{ type: "string" }, { type: "integer" }],
                description: "Optional search mode and page limit."
              }
            },
            required: ["query"],
            unevaluatedProperties: false
          },
          annotations: { readOnlyHint: true }
        },
        {
          name: "list_sessions",
          description: "List active sessions without modifying state.",
          inputSchema: {
            $schema: "https://json-schema.org/draft/2020-12/schema",
            type: "object",
            properties: {},
            unevaluatedProperties: false
          },
          outputSchema: {
            $schema: "https://json-schema.org/draft/2019-09/schema",
            type: "object",
            properties: {
              count: { type: "integer" }
            }
          },
          annotations: { readOnlyHint: true }
        }
      ]
    }
  },
};
