const assert = require('assert');
const validate = require('./config').validateApiBaseUrl;
assert.throws(() => validate('', false));
assert.throws(() => validate('http://localhost:8080', false));
assert.throws(() => validate('https://127.0.0.1', false));
assert.strictEqual(validate('https://shop.example/', false), 'https://shop.example');
assert.strictEqual(validate('http://localhost:8080', true), 'http://localhost:8080');
console.log('Mini-program configuration: 5 assertions passed');
