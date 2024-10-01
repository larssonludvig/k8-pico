using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Json;
using System.Threading.Tasks;
using System.Text.Json;

namespace k8_pico_frontend.Components.ApiService {
    public class ApiService {
        private static HttpClient? client = null;

        public void initialize(string baseAddress) {
            client = new HttpClient();
            client.BaseAddress = new System.Uri(baseAddress + "/api/");
            Console.WriteLine("Client initialized with base address: " + baseAddress);
        }

        public void setBaseAddress(string baseAddress) {
            initialize(baseAddress);
        }

        public async Task<T> Get<T>(string endpoint) {
            if (client == null) {
                throw new System.Exception("Client not initialized");
            }

            var response = await client.GetAsync(endpoint);

            if (response.IsSuccessStatusCode) {
                string json = await response.Content.ReadAsStringAsync();

                T? data = JsonSerializer.Deserialize<T>(
                    json, 
                    new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    }
                );

                if (data == null) {
                    throw new System.Exception("Failed to deserialize response");
                }

                return data;
            }
            throw new System.Exception("Failed to fetch data");
        }

        public async Task<T> Post<T>(string endpoint, object data) {
            if (client == null) {
                throw new System.Exception("Client not initialized");
            }

            var response = await client.PostAsJsonAsync(endpoint, data);

            if (response.IsSuccessStatusCode) {
                string json = await response.Content.ReadAsStringAsync();

                T? responseData = JsonSerializer.Deserialize<T>(
                    json, 
                    new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    }
                );

                if (responseData == null) {
                    throw new System.Exception("Failed to deserialize response");
                }

                return responseData;
            }
            throw new System.Exception("Failed to fetch data");
        }

        public async Task<T> Put<T>(string endpoint, object data) {
            if (client == null) {
                throw new System.Exception("Client not initialized");
            }
            
            var response = await client.PutAsJsonAsync(endpoint, data);

            if (response.IsSuccessStatusCode) {
                string json = await response.Content.ReadAsStringAsync();

                T? responseData = JsonSerializer.Deserialize<T>(
                    json, 
                    new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    }
                );

                if (responseData == null) {
                    throw new System.Exception("Failed to deserialize response");
                }

                return responseData;
            }
            throw new System.Exception("Failed to fetch data");
        }

        public async Task<T> Delete<T>(string endpoint) {
            if (client == null) {
                throw new System.Exception("Client not initialized");
            }
            
            var response = await client.DeleteAsync(endpoint);

            if (response.IsSuccessStatusCode) {
                string json = await response.Content.ReadAsStringAsync();

                T? responseData = JsonSerializer.Deserialize<T>(
                    json, 
                    new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    }
                );

                if (responseData == null) {
                    throw new System.Exception("Failed to deserialize response");
                }

                return responseData;
            }
            throw new System.Exception("Failed to fetch data");
        }
    }
}