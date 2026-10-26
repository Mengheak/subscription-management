/** Node.js 18+ server-side helper. Never bundle an API key into browser JavaScript. */
export class MerchantClient {
  constructor({ baseUrl = process.env.BILLING_API_URL, apiKey = process.env.BILLING_API_KEY } = {}) {
    if (!baseUrl || !apiKey) throw new Error('BILLING_API_URL and BILLING_API_KEY are required');
    this.baseUrl = baseUrl.replace(/\/$/, '') + '/api/v1/merchant';
    this.apiKey = apiKey;
  }
  async request(path, { method = 'GET', body, query } = {}) {
    const url = new URL(this.baseUrl + path);
    for (const [key, value] of Object.entries(query || {})) if (value != null) url.searchParams.set(key, String(value));
    const response = await fetch(url, {
      method, headers: { 'X-API-Key': this.apiKey, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(20000), cache: 'no-store',
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
      const error = new Error(payload.message || 'Merchant request failed');
      error.status = response.status;
      error.code = payload.data?.errorCode;
      throw error;
    }
    return payload.data;
  }
  createCustomer(customer) { return this.request('/customers', { method: 'POST', body: customer }); }
  customer(externalId) { return this.request('/customers/lookup', { query: { externalId } }); }
  subscribe(subscription) { return this.request('/subscriptions', { method: 'POST', body: subscription }); }
  access(query) { return this.request('/access', { query }); }
  recordUsage(event) { return this.request('/usage/events', { method: 'POST', body: event }); }
  usage(subscriptionId) { return this.request(`/subscriptions/${encodeURIComponent(subscriptionId)}/usage`); }
  invoices(customerId, page = 0) { return this.request('/invoices', { query: { customerId, page, size: 20 } }); }
  cancel(subscriptionId, atPeriodEnd = true) { return this.request(`/subscriptions/${encodeURIComponent(subscriptionId)}/cancel`, { method: 'POST', body: { atPeriodEnd } }); }
}
