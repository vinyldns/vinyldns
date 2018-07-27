class TestData:
    A = {
        'zoneId': None,
        'name': 'test-create-a-ok',
        'type': 'A',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'address': '10.1.1.1'
            },
            {
                'address': '10.2.2.2'
            }
        ]
    }
    AAAA = {
        'zoneId': None,
        'name': 'test-create-aaaa-ok',
        'type': 'AAAA',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'address': '2001:db8:0:0:0:0:0:3'
            },
            {
                'address': '2002:db8:0:0:0:0:0:3'
            }
        ]
    }
    CNAME = {
        'zoneId': None,
        'name': 'test-create-cname-ok',
        'type': 'CNAME',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'cname': 'cname.'
            }
        ]
    }
    MX = {
        'zoneId': None,
        'name': 'test-create-mx-ok',
        'type': 'MX',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'preference': 100,
                'exchange': 'exchange.'
            }
        ]
    }
    PTR = {
        'zoneId': None,
        'name': '10.20',
        'type': 'PTR',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'ptrdname': 'ptr.'
            }
        ]
    }
    SPF = {
        'zoneId': None,
        'name': 'test-create-spf-ok',
        'type': 'SPF',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'text': 'spf.'
            }
        ]
    }
    SRV = {
        'zoneId': None,
        'name': 'test-create-srv-ok',
        'type': 'SRV',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'priority': 1,
                'weight': 2,
                'port': 8000,
                'target': 'srv.'
            }
        ]
    }
    SSHFP = {
        'zoneId': None,
        'name': 'test-create-sshfp-ok',
        'type': 'SSHFP',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'algorithm': 1,
                'type': 2,
                'fingerprint': 'fp'
            }
        ]
    }
    TXT = {
        'zoneId': None,
        'name': 'test-create-txt-ok',
        'type': 'TXT',
        'ttl': 100,
        'account': 'foo',
        'records': [
            {
                'text': 'some text'
            }
        ]
    }
    RECORDS = [('A', A), ('AAAA', AAAA), ('CNAME', CNAME), ('MX', MX), ('PTR', PTR), ('SPF', SPF), ('SRV', SRV), ('SSHFP', SSHFP), ('TXT', TXT)]
    FORWARD_RECORDS = [('A', A), ('AAAA', AAAA), ('CNAME', CNAME), ('MX', MX), ('SPF', SPF), ('SRV', SRV), ('SSHFP', SSHFP), ('TXT', TXT)]
    REVERSE_RECORDS = [('CNAME', CNAME), ('PTR', PTR), ('TXT', TXT)]
