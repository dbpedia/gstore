<?php

namespace Tests\integration\store;

use Tests\ARC2_TestCase;

class ARC2_StoreAskQueryHandlerTest extends ARC2_TestCase
{
    protected $store;

    public function setUp()
    {
        parent::setUp();

        $this->store = \ARC2::getStore($this->dbConfig);
        $this->store->drop();
        $this->store->setup();

        $this->fixture = new \ARC2_StoreAskQueryHandler($this->store->a, $this->store);
    }

    /*
     * Tests for __init
     */

    /**
     * @doesNotPerformAssertions
     */
    public function test__init()
    {
        $this->fixture = new \ARC2_StoreAskQueryHandler($this->store->a, $this->store);
        $this->fixture->__init();
        $this->assertEquals($this->store, $this->fixture->store);
    }
}
